package canal11.gestion.repository;

import canal11.model.Usuario;

import java.util.Optional;

/** Busqueda de usuarios que necesita el router (permite simularla en tests). */
public interface UserLookup {
    Optional<Usuario> findByEmail(String email);
}
