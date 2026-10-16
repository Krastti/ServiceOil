package ru.krastti.entity;

/** Values match the existing Flyway CHECK constraints; stored as text, never ordinals. */
public final class PersistenceValues {
    private PersistenceValues() {}
    public enum DataMode { OPEN_DATA, DEMO }
    public enum Frequency { WEEKLY }
    public enum Currency { RUB }
    public enum Unit { LITER }
    public enum ImportStatus { SUCCESS, FAILED }
}
