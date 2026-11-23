package pe.com.rebolsysBackend.service.impl.mantenimiento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteResumenResponseDTO;
import pe.com.rebolsysBackend.entity.Huesped;
import pe.com.rebolsysBackend.entity.Perfil;
import pe.com.rebolsysBackend.entity.PersonaJuridica;
import pe.com.rebolsysBackend.entity.PersonaNatural;
import pe.com.rebolsysBackend.entity.TipoDocumento;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import pe.com.rebolsysBackend.repository.mantenimiento.HuespedRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PerfilRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PersonaJuridicaRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PersonaNaturalRepository;
import pe.com.rebolsysBackend.repository.comun.documentos.TipoDocumentoRepository;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

  private final TipoDocumentoRepository tiposDocumento;
  private final PersonaJuridicaRepository juridicas;
  private final PersonaNaturalRepository naturales;
  private final PerfilRepository perfiles;
  private final HuespedRepository huespedes;
  private final ReglasPropuesta reglas;

  @Override
  @Transactional(readOnly = true)
  public ClienteResumenResponseDTO buscar(Long tipo, String numero) {
    var perfil =
        perfiles
            .findByTipoDocumentoIdTipoDocumentoAndNumeroDocumento(tipo, numero.trim())
            .orElseThrow(
                () ->
                    new RecursoNoEncontradoException(
                        "No hay un cliente registrado con ese documento. Completa sus datos."));
    return new ClienteResumenResponseDTO(
        perfil.getEsCliente() == 1 ? perfil.getIdPerfil() : null,
        datos(perfil));
  }

  @Override
  @Transactional(readOnly = true)
  public ClienteRegistroRequestDTO datos(Perfil perfil) {
    var natural = naturales.findById(perfil.getIdPerfil()).orElse(null);
    var empresa = juridicas.findById(perfil.getIdPerfil()).orElse(null);
    return new ClienteRegistroRequestDTO(
        perfil.getTipoDocumento().getIdTipoDocumento(),
        perfil.getNumeroDocumento(),
        empresa != null,
        natural == null ? null : natural.getNombres(),
        natural == null ? null : natural.getApellidos(),
        natural == null ? null : natural.getFechaNacimiento(),
        empresa == null ? null : empresa.getRazonSocial(),
        empresa == null ? null : empresa.getDireccionFiscal(),
        perfil.getCorreo(),
        perfil.getTelefono());
  }

  @Override
  @Transactional
  public Perfil guardarPersona(ClienteRegistroRequestDTO datos) {
    reglas.validar(datos);
    TipoDocumento tipo = tiposDocumento.findById(datos.idTipoDocumento()).orElse(null);
    if (tipo == null || "INTERNO".equalsIgnoreCase(tipo.getNombreDocumento()))
      throw new ReglaNegocioException("Selecciona un tipo de documento válido");
    var found =
        perfiles.findByTipoDocumentoIdTipoDocumentoAndNumeroDocumento(
            datos.idTipoDocumento(), datos.numeroDocumento().trim());
    if (found.isPresent()) {
      var perfil = found.get();
      boolean juridica = juridicas.findById(perfil.getIdPerfil()).orElse(null) != null;
      if (juridica != Boolean.TRUE.equals(datos.empresa()))
        throw new ReglaNegocioException("El documento ya corresponde a otro tipo de persona");
      if (!juridica) {
        var natural = naturales.findById(perfil.getIdPerfil()).orElseThrow();
        if (natural.getFechaNacimiento() == null && datos.fechaNacimiento() != null) {
          natural.setFechaNacimiento(datos.fechaNacimiento());
          naturales.saveAndFlush(natural);
        } else if (datos.fechaNacimiento() != null
            && !datos.fechaNacimiento().equals(natural.getFechaNacimiento())) {
          throw new ReglaNegocioException(
              "La fecha de nacimiento no coincide con la persona registrada; revisa sus datos");
        }
      }
      return perfil;
    }
    var perfil = new Perfil();
    perfil.setTipoDocumento(tipo);
    perfil.setNumeroDocumento(datos.numeroDocumento().trim());
    perfil.setCorreo(limpiar(datos.correo()));
    perfil.setTelefono(limpiar(datos.telefono()));
    perfiles.save(perfil);
    if (Boolean.TRUE.equals(datos.empresa())) {
      var empresa = new PersonaJuridica();
      empresa.setPerfil(perfil);
      empresa.setRazonSocial(datos.razonSocial().trim());
      empresa.setDireccionFiscal(limpiar(datos.direccionFiscal()));
      juridicas.save(empresa);
    } else {
      var natural = new PersonaNatural();
      natural.setPerfil(perfil);
      natural.setNombres(datos.nombres().trim());
      natural.setApellidos(datos.apellidos().trim());
      natural.setFechaNacimiento(datos.fechaNacimiento());
      naturales.save(natural);
    }
    return perfil;
  }

  @Override
  @Transactional
  public Perfil guardarCliente(ClienteRegistroRequestDTO datos) {
    Perfil perfil = guardarPersona(datos);
    perfil.setEsCliente(1);
    return perfil;
  }

  @Override
  @Transactional
  public Huesped obtenerOCrearHuesped(Perfil perfil) {
    var cliente = perfiles.bloquearCliente(perfil.getIdPerfil())
        .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
    if (!naturales.existsById(cliente.getIdPerfil())) {
      throw new ReglaNegocioException("Solo una persona natural puede ser huésped");
    }
    return huespedes.findByPerfilIdPerfil(cliente.getIdPerfil()).orElseGet(() -> {
      var huesped = new Huesped();
      huesped.setPerfil(cliente);
      return huespedes.save(huesped);
    });
  }

  @Override
  @Transactional(readOnly = true)
  public String nombre(Perfil perfil) {
    ClienteRegistroRequestDTO datos = datos(perfil);
    return Boolean.TRUE.equals(datos.empresa())
        ? datos.razonSocial()
        : datos.nombres() + " " + datos.apellidos();
  }

  @Override
  @Transactional
  public Perfil actualizarPersona(Long idPerfil, ClienteRegistroRequestDTO datos) {
    reglas.validar(datos);
    Perfil perfil =
        perfiles
            .findById(idPerfil)
            .orElseThrow(() -> new RecursoNoEncontradoException("La persona no existe"));
    var existente =
        perfiles.findByTipoDocumentoIdTipoDocumentoAndNumeroDocumento(
            datos.idTipoDocumento(), datos.numeroDocumento().trim());
    if (existente.isPresent() && !existente.get().getIdPerfil().equals(idPerfil))
      throw new ReglaNegocioException("El documento ya pertenece a otra persona registrada");
    var natural = naturales.findById(idPerfil).orElse(null);
    var juridica = juridicas.findById(idPerfil).orElse(null);
    if (Boolean.TRUE.equals(datos.empresa()) != (juridica != null))
      throw new ReglaNegocioException(
          "No se puede convertir una persona natural en empresa ni una empresa en persona natural"
              + " desde la reserva");
    perfil.setTipoDocumento(
        tiposDocumento
            .findById(datos.idTipoDocumento())
            .orElseThrow(() -> new RecursoNoEncontradoException("El tipo de documento no existe")));
    perfil.setNumeroDocumento(datos.numeroDocumento().trim());
    perfil.setCorreo(limpiar(datos.correo()));
    perfil.setTelefono(limpiar(datos.telefono()));
    if (natural != null) {
      natural.setNombres(datos.nombres().trim());
      natural.setApellidos(datos.apellidos().trim());
      natural.setFechaNacimiento(datos.fechaNacimiento());
      naturales.save(natural);
    } else {
      juridica.setRazonSocial(datos.razonSocial().trim());
      juridica.setDireccionFiscal(limpiar(datos.direccionFiscal()));
      juridicas.save(juridica);
    }
    return perfiles.save(perfil);
  }

  private String limpiar(String texto) {
    return texto == null || texto.isBlank() ? null : texto.trim();
  }

  @Override
  @Transactional(readOnly = true)
  public pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO obtener(Long id) {
    var c =
        perfiles
            .findByIdPerfilAndEsCliente(id, 1)
            .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
    return new pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO(
        c.getIdPerfil(), datos(c), huespedes.findByPerfilIdPerfil(id).map(Huesped::getRestriccionesMedicas).orElse(null));
  }

  @Override
  @Transactional
  public pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO guardar(
      Long id, pe.com.rebolsysBackend.dto.mantenimiento.ClienteGuardarDTO r) {
    reglas.validar(r);
    Perfil c;
    if (id == null) c = guardarCliente(r.persona());
    else {
      c =
          perfiles
              .bloquearCliente(id)
              .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
      actualizarPersona(c.getIdPerfil(), r.persona());
    }
    var restricciones = limpiar(r.restriccionesMedicas());
    if (restricciones != null || huespedes.findByPerfilIdPerfil(c.getIdPerfil()).isPresent()) {
      obtenerOCrearHuesped(c).setRestriccionesMedicas(restricciones);
    }
    return obtener(c.getIdPerfil());
  }
}
