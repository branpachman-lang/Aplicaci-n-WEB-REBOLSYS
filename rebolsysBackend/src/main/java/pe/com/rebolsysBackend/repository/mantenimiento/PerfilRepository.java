package pe.com.rebolsysBackend.repository.mantenimiento;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.com.rebolsysBackend.entity.Perfil;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {
    Optional<Perfil> findByTipoDocumentoIdTipoDocumentoAndNumeroDocumento(Long tipo, String numero);

    Optional<Perfil> findFirstByTipoDocumentoIdTipoDocumentoAndNumeroDocumentoIgnoreCaseOrderByIdPerfil(
        Long tipo, String numero);

    Optional<Perfil> findByIdPerfilAndEsCliente(Long idPerfil, Integer esCliente);

    @Query("""
        select p from Perfil p join fetch p.tipoDocumento d
        left join PersonaNatural n on n.perfil = p
        left join PersonaJuridica j on j.perfil = p
        where p.esCliente = 1
          and ((:empresa = true and j.idPerfil is not null and d.codigoSunat = '6')
            or (:empresa = false and n.idPerfil is not null and d.codigoSunat in ('1', '4', '7')))
          and (:tipo is null or d.idTipoDocumento = :tipo)
          and (:numero is null or upper(p.numeroDocumento) = :numero)
          and (:nombre is null or lower(trim(concat(concat(n.nombres, ' '), n.apellidos))) like :nombre escape '!'
            or lower(j.razonSocial) like :nombre escape '!')
        order by coalesce(j.razonSocial, n.nombres), n.apellidos, p.idPerfil
        """)
    List<Perfil> buscarTitulares(boolean empresa, Long tipo, String numero, String nombre, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Perfil p where p.idPerfil=:id and p.esCliente=1")
    Optional<Perfil> bloquearCliente(Long id);
}
