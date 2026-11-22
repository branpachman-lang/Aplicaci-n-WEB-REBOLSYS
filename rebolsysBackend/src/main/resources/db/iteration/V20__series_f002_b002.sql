-- Ejecutar una vez antes de usar F002/B002. No modifica documentos emitidos.
-- Puede repetirse: nunca reinicia ni reduce un contador existente.
MERGE INTO serie_comprobante destino
USING (
  SELECT t.idTipoComprobante,
         CASE t.codigo WHEN 'FACTURA' THEN 'F002' ELSE 'B002' END serie,
         NVL(MAX(CASE WHEN REGEXP_LIKE(c.numeroComprobante, '^[0-9]{1,8}$')
                      THEN TO_NUMBER(c.numeroComprobante) END), 0) + 1 siguienteNumero
  FROM tipo_comprobante t
  LEFT JOIN comprobante c ON c.idTipoComprobante = t.idTipoComprobante
    AND c.serie = CASE t.codigo WHEN 'FACTURA' THEN 'F002' ELSE 'B002' END
  WHERE t.codigo IN ('FACTURA', 'BOLETA')
  GROUP BY t.idTipoComprobante, t.codigo
) origen
ON (destino.idTipoComprobante = origen.idTipoComprobante AND destino.serie = origen.serie)
WHEN MATCHED THEN UPDATE SET destino.siguienteNumero = GREATEST(destino.siguienteNumero, origen.siguienteNumero)
WHEN NOT MATCHED THEN INSERT (idTipoComprobante, serie, siguienteNumero)
  VALUES (origen.idTipoComprobante, origen.serie, origen.siguienteNumero);

COMMIT;
