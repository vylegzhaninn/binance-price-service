create table price_records
(
    id         bigserial primary key,
    symbol     varchar(20) not null,
    price      numeric     not null, -- exact decimal, keeps the scale received from Binance
    fetched_at timestamptz not null
);

create index idx_price_records_symbol_fetched_at
    on price_records (symbol, fetched_at desc);
