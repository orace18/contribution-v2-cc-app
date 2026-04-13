package com.ccapp.mlm.repository.projection;

public interface MlmTreeMemberProjection {

    String getMemberId();

    String getParentId();

    Integer getGeneration();

    /**
     * Returns the {@code created_at} timestamp cast to text by the native query.
     * Using {@code String} avoids the JDBC-driver incompatibility between H2 (which
     * returns {@code OffsetDateTime} for TIMESTAMP WITH TIME ZONE) and PostgreSQL
     * (which returns {@code Instant}), both of which cause
     * {@code UnsupportedOperationException} when the projection interface declares
     * the other type. Callers must parse this value with
     * {@link java.time.OffsetDateTime#parse} or
     * {@link java.time.Instant#parse} + zone conversion.
     */
    String getLinkedAt();
}

