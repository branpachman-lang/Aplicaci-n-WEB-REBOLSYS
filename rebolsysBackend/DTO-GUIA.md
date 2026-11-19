# DTOs del backend

Los contratos de entrada y salida usan `record` de Java 21. Conservan los nombres
JSON que consume Angular. Para acceder a un dato usa `dto.nombreProducto()`;
para cambiarlo construye una instancia nueva, en lugar de usar setters.

## Organización y limpieza

- `dto/` contiene 71 archivos activos: 66 records y cinco contenedores de records
  por módulo (`CajaDTO`, `LimpiezaDTO`, `ReportesDTO`, `SeguridadDTO` y `SistemaDTO`).
- El formato común de errores está en `exception/dto/ErrorResponseDTO`.
- Se eliminaron 61 DTOs sin referencias en fuentes, pruebas ni archivos versionados.
  Los módulos de seguridad, caja y limpieza utilizan sus contratos específicos;
  no necesitan las antiguas parejas genéricas Request/Response de cada tabla.
- Las entidades JPA mantienen sus clases y su modelo de persistencia.

## Compatibilidad y validación

Mantén las restricciones de Jakarta Validation en los componentes del record y
`@Valid` en los objetos anidados. La validación sigue ejecutándose en la entrada
HTTP y en los servicios que la invocan explícitamente.

`ProductoRequestDTO` distingue un stock omitido (cero) de un `null` explícito
(error de validación), mediante `EnteroCeroSiAusenteDeserializer`. Conserva esa
distinción al modificar el contrato. `stockBajo` continúa siendo un campo calculado
en la respuesta y `isIdentidadValida()` valida la identidad sin exponerse en JSON.

Los ejemplos de `src/test/resources/dto/contratos-json.json` se capturaron antes
de la migración. Verifican campos, valores y orden de serialización, incluidos
los datos anidados del cliente. Actualízalos solo ante un cambio deliberado del
contrato, revisando también su impacto en las firmas de solicitudes.

## Verificación

Ejecuta `./mvnw.cmd test`. Las pruebas cubren compatibilidad JSON, validaciones,
asignación del operador desde la sesión y privacidad de las reservas por permiso.
Las pruebas Oracle continúan siendo opcionales con `RUN_ORACLE_TESTS=true`.
