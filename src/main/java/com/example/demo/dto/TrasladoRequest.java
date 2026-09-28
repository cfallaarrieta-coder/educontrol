package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record TrasladoRequest(

        @NotNull(message = "Selecciona la seccion de destino")
        Integer seccionDestinoId

) {}
