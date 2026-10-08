package com.mullagaliev.aqpbalancer.model;

/**
 * Описание запроса, пришедшего от клиента.
 * Неизменяемый: создаётся один раз и не меняется после создания.
 */
public record QueryRequest(
    /** Уникальный идентификатор запроса */
    long id,

    /** Текст SQL-запроса*/
    String sql,

    /** Ожидаемое количество строк в результате */
    long estimatedRows,

    /** Тип запроса */
    QueryType type,

    /** Скошенность данных: 0 — симметрично, >0 — хвост справа, <0 — слева */
    double skewness,

    /** Время прихода запроса*/
    long arrivalTimeMs
) {}