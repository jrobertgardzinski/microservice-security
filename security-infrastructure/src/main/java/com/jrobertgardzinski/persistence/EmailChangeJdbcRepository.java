package com.jrobertgardzinski.persistence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.sql.DataSource;

@JdbcRepository(dialect = Dialect.POSTGRES)
@Requires(beans = DataSource.class)
interface EmailChangeJdbcRepository extends CrudRepository<EmailChangeEntity, String> {
}
