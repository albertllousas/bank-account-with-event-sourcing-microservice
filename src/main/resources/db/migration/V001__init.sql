CREATE TABLE public.account_projection
(
    id                              UUID        NOT NULL,
    balance                         DECIMAL(19) NOT NULL,
    status                          TEXT        NOT NULL,
    currency                        TEXT        NOT NULL,
    customer_id                     UUID        NOT NULL,
    type                            TEXT        NOT NULL,
    revision                        BIGINT      NOT NULL,
    pending_out_of_order_updates    INT         NOT NULL,
    created_at      TIMESTAMPTZ     NOT         NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_account_projection PRIMARY KEY (id)
);

CREATE TABLE idempotency_key
(
    key                              UUID        NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_idempotency_key PRIMARY KEY (key)
);
