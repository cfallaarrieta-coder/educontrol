/* =========================================================
   app.js - codigo compartido por todas las paginas:
     - App.iniciar()   comprueba la sesion y dibuja la navbar
     - App.api()       peticiones AJAX con JSON
     - App.escuchar()  suscripcion a un topic del WebSocket (STOMP)
     - App.crud()      pantalla de mantenimiento generica (tabla + modal)
     - App.toast()     avisos flotantes
   Requiere jQuery y @stomp/stompjs cargados antes.
   ========================================================= */
var App = (function () {

    var usuario = null;

    // =========================================================
    // MENU de la navbar. admin:true = solo lo ve el rol ADMIN
    // =========================================================
    var MENU = [
        { texto: 'Inicio', href: 'inicio.html' },
        {
            texto: 'Mantenimiento', hijos: [
                { texto: 'Alumnos', href: 'alumnos.html' },
                { texto: 'Docentes', href: 'docentes.html' },
                { texto: 'Grados', href: 'grados.html' },
                { texto: 'Secciones', href: 'secciones.html' },
                { texto: 'Años escolares', href: 'anios.html', admin: true },
                { texto: 'Usuarios', href: 'usuarios.html', admin: true }
            ]
        },
        {
            texto: 'Inscripción', hijos: [
                { texto: 'Inscribir alumno', href: 'matricula.html' },
                { texto: 'Lista de inscripciones', href: 'matriculas.html' }
            ]
        },
        { texto: 'Pagos y Caja', href: 'pagos.html' },
        { texto: 'Reportes', href: 'reportes.html' }
    ];

    var NOMBRES = {
        ADMIN: 'Admin', SECRETARIA: 'Secretaria',
        INICIAL: 'Inicial', PRIMARIA: 'Primaria', SECUNDARIA: 'Secundaria',
        MANANA: 'Mañana', TARDE: 'Tarde',
        ABIERTO: 'Abierto', CERRADO: 'Cerrado',
        INSCRITA: 'Inscrito', MATRICULADA: 'Matriculado', ANULADA: 'Anulada',
        DEBE: 'Debe', PAGADO: 'Pagado'
    };

    // =========================================================
    // Utilidades
    // =========================================================

    /** Evita inyectar HTML con datos del usuario. */
    function escapar(texto) {
        return $('<div>').text(texto == null ? '' : String(texto)).html();
    }

    function nombre(valorEnum) {
        return NOMBRES[valorEnum] || valorEnum || '';
    }

    function esAdmin() {
        return usuario && usuario.rol === 'ADMIN';
    }

    function fecha(iso) {
        if (!iso) return '';
        var p = iso.substring(0, 10).split('-');
        return p[2] + '/' + p[1] + '/' + p[0] + (iso.length > 10 ? ' ' + iso.substring(11, 16) : '');
    }

    function hora() {
        return new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    }

    /** Barra de ocupacion de una seccion (verde / naranja / rojo). */
    function ocupacion(disponibles, maximas) {
        var usados = maximas - disponibles;
        var pct = maximas ? Math.round(usados * 100 / maximas) : 0;
        var clase = disponibles === 0 ? 'llena' : (pct >= 75 ? 'media' : '');
        return '<div class="ocupacion ' + clase + '">' +
            '<div class="barra"><div class="relleno" style="width:' + pct + '%"></div></div>' +
            '<small>' + usados + ' / ' + maximas + '</small></div>';
    }

    function etiquetaVacantes(disponibles) {
        return disponibles === 0
            ? '<span class="etiqueta et-rojo">LLENA</span>'
            : '<span class="etiqueta et-verde">' + disponibles + ' libres</span>';
    }

    // =========================================================
    // AJAX
    // =========================================================
    function api(metodo, url, datos) {
        var opciones = { url: url, type: metodo };
        if (datos !== undefined) {
            opciones.contentType = 'application/json';
            opciones.data = JSON.stringify(datos);
        }
        return $.ajax(opciones);
    }

    function mensajeError(xhr) {
        return (xhr && xhr.responseJSON && xhr.responseJSON.mensaje) || 'No se pudo conectar con el servidor';
    }

    // Si cualquier peticion responde 401, la sesion caduco
    $(document).ajaxError(function (evento, xhr) {
        if (xhr.status === 401 && !/login\.html$/.test(location.pathname)) {
            window.location.href = 'login.html';
        }
    });

    // =========================================================
    // TOAST
    // =========================================================
    function toast(texto, tipo) {
        if (!$('#toasts').length) $('body').append('<div id="toasts"></div>');
        var $t = $('<div class="toast ' + (tipo || 'info') + '"></div>').text(texto);
        $('#toasts').append($t);
        setTimeout(function () { $t.fadeOut(400, function () { $t.remove(); }); }, tipo === 'error' ? 6000 : 4000);
    }

    // =========================================================
    // NAVBAR
    // =========================================================
    function dibujarNavbar() {
        var actual = location.pathname.split('/').pop() || 'inicio.html';

        var items = MENU.map(function (item) {
            if (!item.hijos) {
                return '<li class="' + (item.href === actual ? 'activo' : '') + '">' +
                    '<a href="' + item.href + '">' + item.texto + '</a></li>';
            }
            var hijos = item.hijos.filter(function (h) { return !h.admin || esAdmin(); });
            var activo = hijos.some(function (h) { return h.href === actual; });
            return '<li class="desplegable ' + (activo ? 'activo' : '') + '">' +
                '<a>' + item.texto + ' ▾</a><ul>' +
                hijos.map(function (h) {
                    return '<li class="' + (h.href === actual ? 'activo' : '') + '">' +
                        '<a href="' + h.href + '">' + h.texto + '</a></li>';
                }).join('') + '</ul></li>';
        }).join('');

        var html =
            '<nav class="navbar">' +
            '  <a class="marca" href="inicio.html">🏫 Colegio <span>Matrícula</span></a>' +
            '  <button class="hamburguesa" type="button" aria-label="Menú">☰</button>' +
            '  <ul class="menu">' + items + '</ul>' +
            '  <span class="ws-estado" title="Actualización en tiempo real (WebSocket)">Desconectado</span>' +
            '  <div class="usuario desplegable">' +
            '    <a>' + escapar(usuario.nombreCompleto || usuario.username) +
            '      <span class="rol">' + nombre(usuario.rol) + '</span> ▾</a>' +
            '    <ul>' +
            '      <li><a href="perfil.html">Cambiar contraseña</a></li>' +
            '      <li><a id="btnSalir">Cerrar sesión</a></li>' +
            '    </ul>' +
            '  </div>' +
            '</nav>';

        $('#navbar').replaceWith(html);

        $('.hamburguesa').on('click', function () { $('.navbar').toggleClass('abierta'); });

        // En movil (sin hover) los submenus se abren con click
        $('.navbar .desplegable > a').on('click', function () {
            var $li = $(this).parent();
            $('.navbar .desplegable').not($li).removeClass('abierto');
            $li.toggleClass('abierto');
        });
        $(document).on('click', function (e) {
            if (!$(e.target).closest('.desplegable').length) $('.navbar .desplegable').removeClass('abierto');
        });

        $('#btnSalir').on('click', function () {
            api('POST', '/api/auth/logout').always(function () { location.href = 'login.html'; });
        });
    }

    // =========================================================
    // WEBSOCKET (STOMP)
    // =========================================================
    var stomp = null;
    var suscripciones = [];

    function conectarWebSocket() {
        if (typeof StompJs === 'undefined') return;

        stomp = new StompJs.Client({
            brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
            reconnectDelay: 3000
        });

        stomp.onConnect = function () {
            $('.ws-estado').addClass('conectado').text('En vivo');
            suscripciones.forEach(suscribir);
        };
        stomp.onWebSocketClose = function () {
            $('.ws-estado').removeClass('conectado').text('Reconectando...');
        };

        stomp.activate();
    }

    function suscribir(s) {
        stomp.subscribe(s.destino, function (mensaje) { s.callback(JSON.parse(mensaje.body)); });
    }

    /** Registra una funcion que se ejecuta cada vez que llega un mensaje al topic. */
    function escuchar(destino, callback) {
        var s = { destino: destino, callback: callback };
        suscripciones.push(s);
        if (stomp && stomp.connected) suscribir(s);
    }

    // =========================================================
    // INICIO DE CADA PAGINA
    // =========================================================
    /**
     * @param opciones.soloAdmin true = la pagina es solo para ADMIN
     * @returns promesa con el usuario logueado
     */
    function iniciar(opciones) {
        opciones = opciones || {};
        var listo = $.Deferred();

        api('GET', '/api/auth/sesion').done(function (u) {
            usuario = u;

            if (opciones.soloAdmin && !esAdmin()) {
                location.href = 'inicio.html';
                return;
            }

            dibujarNavbar();
            $('.solo-admin').toggle(esAdmin());

            // Aviso global: lo que hacen los DEMAS usuarios aparece en cualquier pantalla
            escuchar('/topic/actividad', function (m) {
                if (m.mensaje.indexOf(usuario.username + ' ') !== 0) toast('🔔 ' + m.mensaje, 'info');
            });
            conectarWebSocket();

            listo.resolve(u);
        });

        return listo.promise();
    }

    // =========================================================
    // CRUD GENERICO (tabla + buscador + filtros + modal)
    // =========================================================
    /**
     * config = {
     *   contenedor: '#crud', url: '/api/alumnos', nombre: 'alumno',
     *   editable: true,                 // muestra Nuevo / Editar / Eliminar
     *   busqueda: 'q',                  // nombre del parametro de busqueda (o false)
     *   filtros: [{ nombre, etiqueta, opciones: fn -> promesa [{valor,texto}], todos: 'Todos' }],
     *   columnas: [{ titulo, valor: fn(fila) -> html }],
     *   campos: [{ nombre, etiqueta, tipo, requerido, opciones, numero, max, min, patron, ayuda, placeholder }],
     *   aFormulario: fn(fila) -> valores del formulario al editar
     * }
     */
    function crud(config) {

        var $c = $(config.contenedor || '#crud');
        var filas = [];
        var editandoId = null;

        var filtrosHtml = (config.filtros || []).map(function (f) {
            return '<div class="campo"><label>' + f.etiqueta + '</label>' +
                '<select data-filtro="' + f.nombre + '"></select></div>';
        }).join('');

        $c.html(
            '<div class="panel">' +
            '  <div class="barra-herramientas">' +
            (config.busqueda ? '<div class="campo ancho"><label>Buscar</label>' +
                '<input type="search" class="crud-buscar" placeholder="Escribe para buscar..."></div>' : '') +
            filtrosHtml +
            '    <div style="margin-left:auto">' +
            (config.editable ? '<button type="button" class="btn-verde crud-nuevo">+ Nuevo ' + config.nombre + '</button>' : '') +
            '    </div>' +
            '  </div>' +
            '  <div class="tabla-scroll"><table>' +
            '    <thead><tr>' + config.columnas.map(function (col) { return '<th>' + col.titulo + '</th>'; }).join('') +
            (config.editable ? '<th></th>' : '') + '</tr></thead>' +
            '    <tbody></tbody>' +
            '  </table></div>' +
            '</div>'
        );

        // ---- filtros ----
        var cargaFiltros = (config.filtros || []).map(function (f) {
            return f.opciones().then(function (ops) {
                var $s = $c.find('[data-filtro="' + f.nombre + '"]');
                if (f.todos) $s.append('<option value="">' + f.todos + '</option>');
                ops.forEach(function (o) { $s.append($('<option>').val(o.valor).text(o.texto)); });
            });
        });

        $c.on('change', '[data-filtro]', recargar);

        var temporizador;
        $c.on('input', '.crud-buscar', function () {
            clearTimeout(temporizador);
            temporizador = setTimeout(recargar, 300);
        });

        function parametros() {
            var p = {};
            if (config.busqueda) {
                var q = $c.find('.crud-buscar').val();
                if (q && q.trim()) p[config.busqueda] = q.trim();
            }
            $c.find('[data-filtro]').each(function () {
                if ($(this).val()) p[$(this).data('filtro')] = $(this).val();
            });
            return p;
        }

        function recargar() {
            var qs = $.param(parametros());
            return api('GET', config.url + (qs ? '?' + qs : '')).done(function (datos) {
                filas = datos;
                pintar();
                if (config.alCargar) config.alCargar(datos);
            });
        }

        function pintar() {
            var $tb = $c.find('tbody').empty();
            var columnas = config.columnas.length + (config.editable ? 1 : 0);

            if (!filas.length) {
                $tb.append('<tr><td colspan="' + columnas + '" class="vacio">No hay registros</td></tr>');
                return;
            }

            filas.forEach(function (fila) {
                var celdas = config.columnas.map(function (col) { return '<td>' + col.valor(fila) + '</td>'; }).join('');
                var acciones = config.editable
                    ? '<td class="acciones">' +
                    '<button type="button" class="btn-chico crud-editar" data-id="' + fila.id + '">Editar</button>' +
                    '<button type="button" class="btn-chico btn-rojo crud-eliminar" data-id="' + fila.id + '">Eliminar</button></td>'
                    : '';
                $tb.append('<tr data-id="' + fila.id + '">' + celdas + acciones + '</tr>');
            });
        }

        // ---- modal ----
        function abrirModal(fila) {
            editandoId = fila ? fila.id : null;
            var valores = fila ? (config.aFormulario ? config.aFormulario(fila) : fila) : {};

            var $fondo = $('<div class="modal-fondo"><form class="modal" novalidate>' +
                '<h2>' + (fila ? 'Editar ' : 'Nuevo ') + config.nombre + '</h2>' +
                '<div class="fila"></div>' +
                '<div class="botones"><button type="button" class="btn-gris crud-cancelar">Cancelar</button>' +
                '<button type="submit" class="crud-guardar">Guardar</button></div>' +
                '</form></div>');

            var $fila = $fondo.find('.fila');
            var cargas = [];

            config.campos.forEach(function (campo) {
                var id = 'campo-' + campo.nombre;
                var $campo = $('<div class="campo"></div>')
                    .append('<label for="' + id + '">' + campo.etiqueta + (campo.requerido ? ' *' : '') + '</label>');
                var $input;

                if (campo.tipo === 'select') {
                    $input = $('<select>');
                    if (!campo.requerido) $input.append('<option value="">(ninguno)</option>');
                    var ops = typeof campo.opciones === 'function' ? campo.opciones() : $.when(campo.opciones);
                    cargas.push(ops.then(function (lista) {
                        lista.forEach(function (o) { $input.append($('<option>').val(o.valor).text(o.texto)); });
                        if (valores[campo.nombre] != null) $input.val(String(valores[campo.nombre]));
                    }));
                } else if (campo.tipo === 'checkbox') {
                    $input = $('<select><option value="true">Sí</option><option value="false">No</option></select>');
                    $input.val(String(valores[campo.nombre] == null ? true : valores[campo.nombre]));
                } else {
                    $input = $('<input>').attr('type', campo.tipo || 'text');
                    if (valores[campo.nombre] != null) $input.val(valores[campo.nombre]);
                    if (campo.max) $input.attr(campo.tipo === 'number' ? 'max' : 'maxlength', campo.max);
                    if (campo.min != null) $input.attr('min', campo.min);
                    if (campo.patron) $input.attr('pattern', campo.patron);
                    if (campo.placeholder) $input.attr('placeholder', campo.placeholder);
                }

                $input.attr({ id: id, name: campo.nombre });
                if (campo.requerido && !(fila && campo.opcionalAlEditar)) $input.prop('required', true);
                $campo.append($input);
                if (campo.ayuda) $campo.append('<small style="color:#888">' + campo.ayuda + '</small>');
                $fila.append($campo);
            });

            $('body').append($fondo);
            $.when.apply($, cargas).then(function () { $fondo.find('input, select').first().focus(); });

            $fondo.on('click', '.crud-cancelar', function () { $fondo.remove(); });
            $fondo.on('submit', 'form', function (e) {
                e.preventDefault();
                var form = this;
                if (!form.checkValidity()) {
                    form.reportValidity();
                    return;
                }
                guardar(leerFormulario($fondo), $fondo);
            });
        }

        function leerFormulario($fondo) {
            var datos = {};
            config.campos.forEach(function (campo) {
                var v = $fondo.find('[name="' + campo.nombre + '"]').val();
                if (v === '' || v == null) v = null;
                else if (campo.tipo === 'checkbox') v = v === 'true';
                else if (campo.tipo === 'number' || campo.numero) v = Number(v);
                else v = v.trim();
                datos[campo.nombre] = v;
            });
            return datos;
        }

        function guardar(datos, $fondo) {
            var $btn = $fondo.find('.crud-guardar').prop('disabled', true).text('Guardando...');
            var peticion = editandoId
                ? api('PUT', config.url + '/' + editandoId, datos)
                : api('POST', config.url, datos);

            peticion.done(function () {
                toast(editandoId ? 'Cambios guardados' : 'Registro creado', 'ok');
                $fondo.remove();
                recargar();
            }).fail(function (xhr) {
                if (xhr.status !== 401) toast(mensajeError(xhr), 'error');
                $btn.prop('disabled', false).text('Guardar');
            });
        }

        $c.on('click', '.crud-nuevo', function () { abrirModal(null); });

        $c.on('click', '.crud-editar', function () {
            var id = $(this).data('id');
            abrirModal(filas.find(function (f) { return f.id === id; }));
        });

        $c.on('click', '.crud-eliminar', function () {
            var id = $(this).data('id');
            if (!confirm('¿Seguro que deseas eliminar este ' + config.nombre + '?')) return;
            api('DELETE', config.url + '/' + id)
                .done(function () { toast('Registro eliminado', 'ok'); recargar(); })
                .fail(function (xhr) { if (xhr.status !== 401) toast(mensajeError(xhr), 'error'); });
        });

        $.when.apply($, cargaFiltros).then(recargar);

        return { recargar: recargar };
    }

    // =========================================================
    // Opciones reutilizables para selects
    // =========================================================
    var opciones = {
        anios: function () {
            return api('GET', '/api/anios').then(function (lista) {
                return lista.map(function (a) { return { valor: a.id, texto: a.anio + ' (' + nombre(a.estado) + ')' }; });
            });
        },
        grados: function () {
            return api('GET', '/api/grados').then(function (lista) {
                return lista.map(function (g) { return { valor: g.id, texto: g.descripcion }; });
            });
        },
        docentes: function () {
            return api('GET', '/api/docentes').then(function (lista) {
                return lista.map(function (d) { return { valor: d.id, texto: d.nombreCompleto }; });
            });
        },
        enumeracion: function (valores) {
            return valores.map(function (v) { return { valor: v, texto: nombre(v) }; });
        }
    };

    return {
        iniciar: iniciar,
        api: api,
        mensajeError: mensajeError,
        escuchar: escuchar,
        crud: crud,
        toast: toast,
        escapar: escapar,
        nombre: nombre,
        fecha: fecha,
        hora: hora,
        ocupacion: ocupacion,
        etiquetaVacantes: etiquetaVacantes,
        esAdmin: esAdmin,
        opciones: opciones,
        usuario: function () { return usuario; }
    };
})();
