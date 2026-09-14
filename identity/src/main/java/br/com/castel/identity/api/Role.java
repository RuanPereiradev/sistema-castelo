package br.com.castel.identity.api;

/** Permission profile assignable to a user. */
public enum Role {

    ADMIN,
    FRONT_DESK,
    WAITER,
    KITCHEN;

    /** Spring Security authority for this role, in the {@code ROLE_<NAME>} format {@code hasRole(...)} expects. */
    public String springAuthority() {
        return "ROLE_" + name();
    }
}
