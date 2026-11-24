-- Ejecutar en el esquema de REBOLSYS con la aplicación detenida.
-- Revisar los resultados antes de confirmar manualmente con COMMIT.
SELECT h.numeroCuarto, COUNT(*) AS items_a_corregir
FROM item i
JOIN habitacion h ON h.idHabitacion = i.idHabitacion
WHERE i.estadoItem = 'ACTIVO'
GROUP BY h.numeroCuarto
ORDER BY h.numeroCuarto;

UPDATE item
SET estadoItem = 'EN_HABITACION'
WHERE idHabitacion IS NOT NULL AND estadoItem = 'ACTIVO';

SELECT h.numeroCuarto,
       COUNT(*) AS items_con_habitacion,
       SUM(CASE WHEN i.estadoItem = 'EN_HABITACION' THEN 1 ELSE 0 END) AS items_para_informe
FROM item i
JOIN habitacion h ON h.idHabitacion = i.idHabitacion
GROUP BY h.numeroCuarto
ORDER BY h.numeroCuarto;
