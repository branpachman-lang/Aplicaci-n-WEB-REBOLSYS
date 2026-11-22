package pe.com.rebolsysBackend.service.impl;

import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.exigir;
import static pe.com.rebolsysBackend.config.CacheConfig.TIPOS_ITEM;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.entity.TipoItem;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.repository.*;
import pe.com.rebolsysBackend.service.service.TipoItemService;

@Service
@RequiredArgsConstructor
public class TipoItemServiceImpl implements TipoItemService {
  private final TipoItemRepository tipos;
  private final ItemRepository items;
  private final ReglasPropuesta reglas;

  @Override
  @Transactional
  @CacheEvict(cacheNames = TIPOS_ITEM, allEntries = true)
  public TipoItemResponseDTO create(TipoItemRequestDTO r) {
    reglas.validar(r);
    var t = new TipoItem();
    t.setNombreItem(r.nombreItem().trim());
    return respuesta(tipos.saveAndFlush(t));
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = TIPOS_ITEM, allEntries = true)
  public TipoItemResponseDTO update(Long id, TipoItemRequestDTO r) {
    reglas.validar(r);
    var t = buscar(id);
    t.setNombreItem(r.nombreItem().trim());
    return respuesta(tipos.saveAndFlush(t));
  }

  @Override
  @Transactional(readOnly = true)
  public TipoItemResponseDTO read(Long id) {
    return respuesta(buscar(id));
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = TIPOS_ITEM, key = "'todos'")
  public List<TipoItemResponseDTO> readAll() {
    return tipos.findAll(Sort.by("nombreItem")).stream().map(this::respuesta).toList();
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = TIPOS_ITEM, allEntries = true)
  public void delete(Long id) {
    var t = buscar(id);
    exigir(!items.existsByTipoItemIdTipoItem(id), "El tipo tiene ítems asociados");
    tipos.delete(t);
    tipos.flush();
  }

  private TipoItem buscar(Long id) {
    return tipos.findById(id)
        .orElseThrow(() -> new RecursoNoEncontradoException("El tipo de ítem no existe"));
  }

  private TipoItemResponseDTO respuesta(TipoItem t) {
    return new TipoItemResponseDTO(t.getIdTipoItem(), t.getNombreItem());
  }
}
