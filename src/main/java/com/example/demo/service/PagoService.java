package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.NegocioException;
import com.example.demo.repository.CuotaRepository;
import com.example.demo.repository.MatriculaRepository;
import com.example.demo.repository.ReciboRepository;
import com.example.demo.repository.SeccionRepository;
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
    private final ApplicationEventPublisher eventos;

    private static final String[] MESES = {
            "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre"
    };

    public PagoService(MatriculaRepository matriculaRepository,
                       SeccionRepository seccionRepository,
                       ReciboRepository reciboRepository,
                       CuotaRepository cuotaRepository,
                       ApplicationEventPublisher eventos) {
        this.matriculaRepository = matriculaRepository;
        this.seccionRepository = seccionRepository;
        this.reciboRepository = reciboRepository;
        this.cuotaRepository = cuotaRepository;
        this.eventos = eventos;
    }

    /**
     * Consulta el estado de cuenta corriente de un alumno en un anio escolar.
     */
    @Transactional(readOnly = true)
    public CuentaCorrienteResponse obtenerCuentaCorriente(Integer matriculaId) {
        Matricula matricula = matriculaRepository.findById(matriculaId)
                .orElseThrow(() -> new NegocioException("No existe la matricula " + matriculaId));

        BigDecimal costoMatricula = matricula.getSeccion().getGrado().getCostoMatriculaOrDefault();
        boolean matriculaPagada = matricula.getEstado() == EstadoMatricula.MATRICULADA;
        Recibo reciboMatricula = matricula.getReciboMatricula();

        List<Cuota> cuotasEntidad = cuotaRepository.findByMatriculaIdOrderByNumeroCuotaAsc(matriculaId);

        List<CuotaResponse> cuotasResponse = new ArrayList<>();
        boolean mesAnteriorPagado = matriculaPagada;

        BigDecimal totalDeuda = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;

        if (matriculaPagada && reciboMatricula != null) {
            totalPagado = totalPagado.add(reciboMatricula.getMonto());
        } else if (!matricula.estaAnulada()) {
            totalDeuda = totalDeuda.add(costoMatricula);
        }

        for (Cuota c : cuotasEntidad) {
            boolean pagable = matriculaPagada && c.estaDebe() && mesAnteriorPagado;
            cuotasResponse.add(CuotaResponse.desde(c, pagable));

            if (c.estaPagado()) {
                totalPagado = totalPagado.add(c.getMonto());
                mesAnteriorPagado = true;
            } else {
                totalDeuda = totalDeuda.add(c.getMonto());
                mesAnteriorPagado = false;
            }
        }

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
                costoMatricula,
                matriculaPagada,
                reciboMatricula != null ? reciboMatricula.getId() : null,
                reciboMatricula != null ? reciboMatricula.getNumero() : null,
                reciboMatricula != null ? reciboMatricula.getFecha() : null,
                cuotasResponse,
                totalDeuda,
                totalPagado
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

        // BLOQUEO PESIMISTA: ocupa la vacante real de la seccion
        seccion.ocuparVacante();

        BigDecimal monto = seccion.getGrado().getCostoMatriculaOrDefault();
        String concepto = "Derecho de Matricula " + seccion.getAnioEscolar().getAnio();
        String metodo = request != null ? request.metodoPago() : "EFECTIVO";

        Recibo recibo = new Recibo(generarNumeroRecibo(), matricula, concepto, monto, cajero, metodo);
        recibo = reciboRepository.save(recibo);

        matricula.setEstado(EstadoMatricula.MATRICULADA);
        matricula.setReciboMatricula(recibo);
        matriculaRepository.save(matricula);

        // Generar las 10 cuotas mensuales (Marzo a Diciembre)
        int anio = seccion.getAnioEscolar().getAnio();
        BigDecimal costoMensual = seccion.getGrado().getCostoMensualidadOrDefault();
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
        return reciboRepository.findById(id)
                .map(ReciboResponse::desde)
                .orElseThrow(() -> new NegocioException("No existe el recibo " + id));
    }

    private synchronized String generarNumeroRecibo() {
        int siguiente = reciboRepository.obtenerUltimoId() + 1;
        return String.format("REC-%06d", siguiente);
    }

    private void avisar(List<Seccion> secciones, String actividad) {
        eventos.publishEvent(new VacantesCambiadasEvent(
                secciones.stream().map(VacanteMensaje::desde).toList(), actividad));
    }
}
