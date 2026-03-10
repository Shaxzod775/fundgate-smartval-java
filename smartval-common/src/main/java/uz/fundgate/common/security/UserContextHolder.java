package uz.fundgate.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserContextHolder {

    private UserContextHolder() {
    }

    public static UserContext getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserContext ctx) {
            return ctx;
        }
        return null;
    }

    public static String getCurrentUid() {
        UserContext ctx = getCurrentUser();
        return ctx != null ? ctx.getUid() : null;
    }
}
