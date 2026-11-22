package pe.com.rebolsysBackend.service.impl;

import static pe.com.rebolsysBackend.config.CacheConfig.REFERENCIAS;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.CatalogoItemResponseDTO;
import pe.com.rebolsysBackend.repository.FormaPagoRepository;
import pe.com.rebolsysBackend.repository.TipoDocumentoRepository;
import pe.com.rebolsysBackend.repository.TipoMovimientoCajaRepository;

/** Solo referencias compartidas; usuarios, permisos y turnos se leen en vivo. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogosEstaticosService {
  private final TipoDocumentoRepository documentos;
  private final FormaPagoRepository pagos;
  private final TipoMovimientoCajaRepository gastos;

  @Cacheable(cacheNames = REFERENCIAS, key = "'documentos'")
  public List<CatalogoItemResponseDTO> documentos() {
    return documentos.findByNombreDocumentoNotOrderByIdTipoDocumento("INTERNO").stream()
        .map(t -> new CatalogoItemResponseDTO(
            t.getIdTipoDocumento(), t.getNombreDocumento(), t.getCodigoSunat())).toList();
  }

  @Cacheable(cacheNames = REFERENCIAS, key = "'pagos'")
  public List<CatalogoItemResponseDTO> pagos() {
    return pagos.findAll().stream()
        .map(t -> new CatalogoItemResponseDTO(t.getIdFormaPago(), t.getNombrePago())).toList();
  }

  @Cacheable(cacheNames = REFERENCIAS, key = "'gastos'")
  public List<CatalogoItemResponseDTO> gastos() {
    return gastos.findAll().stream().filter(t -> t.getSigno() == -1)
        .map(t -> new CatalogoItemResponseDTO(t.getIdTipoMovimiento(), t.getNombreMovimiento())).toList();
  }
}
