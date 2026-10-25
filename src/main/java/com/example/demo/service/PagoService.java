package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.CuotaRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.ReciboRepository;
import com.example.demo.repository.SeccionRepository;
import com.example.demo.repository.TarifaRepository;
import com.example.demo.websocket.VacantesCambiadasEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PagoService {

    private final MatriculaRepository matriculaRepository;
    private final SeccionRepository seccionRepository;
    private final ReciboRepository reciboRepository;
    private final CuotaRepository cuotaRepository;
    private final TarifaRepository tarifaRepository;
    private final ApplicationEventPublisher eventos;

    private static final String[] MESES = {
            "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public PagoService(MatriculaRepository matriculaRepository,
                       SeccionRepository seccionRepository,
                       ReciboRepository reciboRepository,
                       CuotaRepository cuotaRepository,
                       TarifaRepository tarifaRepository,
                       ApplicationEventPublisher eventos) {
        this.matriculaRepository = matriculaRepository;
        this.seccionRepository = seccionRepository;
        this.reciboRepository = reciboRepository;
        this.cuotaRepository = cuotaRepository;
        this.tarifaRepository = tarifaRepository;
        this.eventos = eventos;
    }

    /**
     * Consulta el estado de cuenta corriente de un alumno en un anio escolar.
     */
    @Transactional(readOnly = true)
    public CuentaCorrienteResponse obtenerCuentaCorriente(Integer matriculaId) {
        Matricula matricula = matriculaRepository.findById(matriculaId)
                .orElseThrow(() -> new NegocioException("No existe la matricula " + matriculaId));

        BigDecimal costoMatricula = tarifaRepository.findByAnioEscolarIdAndNivel(
                matricula.getSeccion().getAnioEscolar().getId(),
                matricula.getSeccion().getGrado().getNivel()
        ).map(Tarifa::getMontoMatricula).orElse(BigDecimal.ZERO);

        Recibo reciboMatricula = matricula.getReciboMatricula();
        boolean matriculaActiva = matricula.getEstado() == EstadoMatricula.MATRICULADA;
        boolean matriculaPagada = matriculaActiva || reciboMatricula != null;

        List<Cuota> cuotasEntidad = cuotaRepository.findByMatriculaIdOrderByNumeroCuotaAsc(matriculaId);
        List<ReciboResponse> recibosMatricula = reciboRepository.findByMatriculaIdOrderByFechaDesc(matriculaId)
                .stream()
                .map(ReciboResponse::desde)
                .toList();

        List<CuotaResponse> cuotasResponse = new ArrayList<>();
        boolean mesAnteriorPagado = matriculaActiva;

        BigDecimal totalDeuda = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;
        BigDecimal totalDevuelto = BigDecimal.ZERO;

        if (reciboMatricula != null) {
            if (reciboMatricula.getEstado() == EstadoRecibo.DEVUELTO) {
                totalDevuelto = totalDevuelto.add(reciboMatricula.getMonto());
            } else {
                totalPagado = totalPagado.add(reciboMatricula.getMonto());
            }
        } else if (!matricula.estaAnulada()) {
            totalDeuda = totalDeuda.add(costoMatricula);
        }

        for (Cuota c : cuotasEntidad) {
            boolean pagable = matriculaActiva && c.estaDebe() && mesAnteriorPagado;
            cuotasResponse.add(CuotaResponse.desde(c, pagable));

            if (c.estaPagado()) {
                totalPagado = totalPagado.add(c.getMonto());
                mesAnteriorPagado = true;
            } else if (c.estaDevuelta()) {
                totalDevuelto = totalDevuelto.add(c.getMonto());
                mesAnteriorPagado = false;
            } else if (c.estaDebe()) {
                totalDeuda = totalDeuda.add(c.getMonto());
                mesAnteriorPagado = false;
            } else {
                // EstadoCuota.ANULADA -> no suma a la deuda (queda en S/ 0.00)
                mesAnteriorPagado = false;
            }
        }

        Recibo devMatricula = reciboMatricula != null ? reciboMatricula.getBoletaDevolucion() : null;

        return new CuentaCorrienteResponse(
                matricula.getId(),
                matricula.getAlumno().getId(),
                matricula.getAlumno().getDni(),
                matricula.getAlumno().getNombreCompleto(),
                matricula.getAlumno().getApoderado(),
                matricula.getSeccion().getDescripcion(),
                matricula.getSeccion().getGrado().getNivel().name(),
                matricula.getSeccion().getAnioEscolar().getAnio(),
                matricula.getEstado(),
                matricula.getMotivoAnulacion(),
                matricula.getFechaAnulacion(),
                matricula.getAnuladoPor(),
                costoMatricula,
                matriculaPagada,
                reciboMatricula != null ? reciboMatricula.getId() : null,
                reciboMatricula != null ? reciboMatricula.getNumero() : null,
                reciboMatricula != null ? reciboMatricula.getFecha() : null,
                reciboMatricula != null ? reciboMatricula.getEstado() : null,
                devMatricula != null ? devMatricula.getId() : null,
                devMatricula != null ? devMatricula.getNumero() : null,
                cuotasResponse,
                recibosMatricula,
                totalDeuda,
                totalPagado,
                totalDevuelto
        );
    }

    /**
     * RF-02: Cobro de Derecho de Matricula.
     * Cambia el estado de INSCRITA a MATRICULADA, ocupa la vacante (con bloqueo pesimista),
     * emite el recibo correlativo y genera las 10 cuotas mensuales (Marzo a Diciembre).
     */
    @Transactional
    public ReciboResponse pagarMatricula(Integer matriculaId, PagoRequest request, String cajero) {

        Matricula matricula = matriculaRepository.findByIdParaActualizar(matriculaId)
                .orElseThrow(() -> new NegocioException("No existe el registro de matricula " + matriculaId));

        if (matricula.getEstado() == EstadoMatricula.MATRICULADA) {
            throw new NegocioException("El Derecho de Matricula ya fue cancelado anteriormente");
        }
        if (matricula.getEstado() == EstadoMatricula.ANULADA) {
            throw new NegocioException("No se puede pagar la matricula de un registro anulado");
        }

        Seccion seccion = seccionRepository.findByIdParaActualizar(matricula.getSeccion().getId())
                .orElseThrow(() -> new NegocioException("La seccion no existe"));

        if (!seccion.getAnioEscolar().estaAbierto()) {
            throw new NegocioException("El anio escolar " + seccion.getAnioEscolar().getAnio() + " esta cerrado");
        }

        Tarifa tarifa = tarifaRepository.findByAnioEscolarIdAndNivel(seccion.getAnioEscolar().getId(), seccion.getGrado().getNivel())
                .orElseThrow(() -> new NegocioException("No hay tarifas configuradas para el nivel " + seccion.getGrado().getNivel() + " en este año escolar."));

        // BLOQUEO PESIMISTA: ocupa la vacante real de la seccion
        seccion.ocuparVacante();

        BigDecimal monto = tarifa.getMontoMatricula();
        String concepto = "Derecho de Matricula " + seccion.getAnioEscolar().getAnio();
        String metodo = request != null ? request.metodoPago() : "EFECTIVO";

        Recibo recibo = new Recibo(generarNumeroRecibo(), matricula, concepto, monto, cajero, metodo);
        recibo = reciboRepository.save(recibo);

        matricula.setEstado(EstadoMatricula.MATRICULADA);
        matricula.setReciboMatricula(recibo);
        matriculaRepository.save(matricula);

        // Generar las 10 cuotas mensuales (Marzo a Diciembre)
        int anio = seccion.getAnioEscolar().getAnio();
        BigDecimal costoMensual = tarifa.getMontoMensualidad();
        List<Cuota> cuotas = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            int mesNum = i + 3; // 3 = Marzo ... 12 = Diciembre
            int ultimoDia = LocalDate.of(anio, mesNum, 1).lengthOfMonth();
            LocalDate vencimiento = LocalDate.of(anio, mesNum, ultimoDia);

            cuotas.add(new Cuota(matricula, i + 1, MESES[i], costoMensual, EstadoCuota.DEBE, vencimiento));
        }
        cuotaRepository.saveAll(cuotas);

        avisar(List.of(seccion), cajero + " cobro la matricula de "
                + matricula.getAlumno().getNombreCompleto() + " (" + seccion.getDescripcion() + ") - Recibo " + recibo.getNumero());

        return ReciboResponse.desde(recibo);
    }

    /**
     * RF-03 y RF-04: Cobro de cuota mensual respetando el orden prelatorio (secuencial).
     */
    @Transactional
    public ReciboResponse pagarCuota(Integer cuotaId, PagoRequest request, String cajero) {

        Cuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new NegocioException("No existe la cuota mensual " + cuotaId));

        if (cuota.estaPagado()) {
            throw new NegocioException("Esta cuota mensual ya ha sido pagada previamente");
        }
        if (cuota.estaAnulada() || cuota.estaDevuelta()) {
            throw new NegocioException("No se puede cobrar una cuota de una matricula anulada");
        }

        Matricula matricula = cuota.getMatricula();
        if (matricula.getEstado() != EstadoMatricula.MATRICULADA) {
            throw new NegocioException("El alumno debe cancelar primero su Derecho de Matricula para habilitar el pago de mensualidades");
        }

        // Restriccion de orden prelatorio (no pagar mes N si mes N-1 figura como DEBE)
        if (cuota.getNumeroCuota() > 1) {
            boolean mesAnteriorDebe = cuotaRepository.existsByMatriculaIdAndNumeroCuotaAndEstado(
                    matricula.getId(),
                    cuota.getNumeroCuota() - 1,
                    EstadoCuota.DEBE
            );
            if (mesAnteriorDebe) {
                Cuota anterior = cuotaRepository.findByMatriculaIdAndNumeroCuota(
                        matricula.getId(), cuota.getNumeroCuota() - 1).orElse(null);
                String nombreMesAnterior = anterior != null ? anterior.getMes() : ("Cuota " + (cuota.getNumeroCuota() - 1));
                throw new NegocioException("No se puede pagar el mes de " + cuota.getMes()
                        + " porque el mes anterior (" + nombreMesAnterior + ") aun figura en estado DEBE.");
            }
        }

        String concepto = "Mensualidad - " + cuota.getMes() + " (" + matricula.getSeccion().getAnioEscolar().getAnio() + ")";
        String metodo = request != null ? request.metodoPago() : "EFECTIVO";

        Recibo recibo = new Recibo(generarNumeroRecibo(), matricula, concepto, cuota.getMonto(), cajero, metodo);
        recibo = reciboRepository.save(recibo);

        cuota.setEstado(EstadoCuota.PAGADO);
        cuota.setRecibo(recibo);
        cuotaRepository.save(cuota);

        avisar(List.of(matricula.getSeccion()), cajero + " cobro la mensualidad de "
                + cuota.getMes() + " de " + matricula.getAlumno().getNombreCompleto() + " - Recibo " + recibo.getNumero());

        return ReciboResponse.desde(recibo);
    }

    @Transactional(readOnly = true)
    public List<ReciboResponse> listarRecibos(Integer anioId, Integer matriculaId, String texto) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        return reciboRepository.filtrar(anioId, matriculaId, filtro)
                .stream()
                .map(ReciboResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReciboResponse obtenerRecibo(Integer id) {
        return reciboRepository.findDetalleById(id)
                .map(ReciboResponse::desde)
                .orElseThrow(() -> new NegocioException("No existe el recibo " + id));
    }

    public synchronized String generarNumeroRecibo() {
        return generarCorrelativo("REC-");
    }

    public synchronized String generarNumeroDevolucion() {
        return generarCorrelativo("DEV-");
    }

    private String generarCorrelativo(String prefijo) {
        long ultimoPorPrefijo = reciboRepository.findTopByNumeroStartingWithOrderByNumeroDesc(prefijo)
                .map(r -> extraerCorrelativo(r.getNumero(), prefijo))
                .orElse(0L);
        long conteo = reciboRepository.contarPorPrefijo(prefijo);
        long base = Math.max(ultimoPorPrefijo, conteo);

        if ("REC-".equals(prefijo)) {
            Integer ultimoId = reciboRepository.obtenerUltimoId();
            if (ultimoId != null) {
                base = Math.max(base, ultimoId.longValue());
            }
        }

        long siguiente = base + 1;
        String candidato;
        do {
            candidato = String.format("%s%06d", prefijo, siguiente);
            siguiente++;
        } while (reciboRepository.existsByNumero(candidato));
        return candidato;
    }

    private long extraerCorrelativo(String numero, String prefijo) {
        if (numero == null || !numero.startsWith(prefijo)) {
            return 0L;
        }
        try {
            return Long.parseLong(numero.substring(prefijo.length()).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void avisar(List<Seccion> secciones, String actividad) {
        eventos.publishEvent(new VacantesCambiadasEvent(
                secciones.stream().map(VacanteMensaje::desde).toList(), actividad));
    }
}
