package com.p5k.proxycache.proxy;

/**
 * Splits a request URI into its application slug (first path segment) and the
 * backend-relative remainder. {@code /billing/users/1} → slug {@code billing}, remainder
 * {@code /users/1}. {@code /billing} → slug {@code billing}, remainder {@code /}.
 */
public record SlugRouting(String slug, String remainder) {

    public static SlugRouting of(String uri) {
        String p = uri.startsWith("/") ? uri.substring(1) : uri;
        int sep = p.indexOf('/');
        if (sep < 0) {
            return new SlugRouting(p, "/");
        }
        return new SlugRouting(p.substring(0, sep), p.substring(sep));
    }

    public boolean hasSlug() {
        return !slug.isEmpty();
    }
}
