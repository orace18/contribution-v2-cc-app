package com.ccapp.mlm.repository.projection;

public interface MlmHierarchyMemberProjection {
    String getMemberId();

    Integer getLevel();

    String getFirstName();

    String getLastName();

    /**
     * Returns the {@code created_at} timestamp cast to text by the native query.
     * Using {@code String} avoids the JDBC-driver incompatibility between H2
     * (returns {@code OffsetDateTime}) and PostgreSQL (returns {@code Instant})
     * for {@code timestamptz} columns — both throw {@code UnsupportedOperationException}
     * if the projection interface declares the wrong concrete type.
     */
    String getLinkedAt();
}
