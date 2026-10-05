package br.com.castel.finance.domain;

/**
 * What the money was spent on — the simplified chart of accounts of the operation (decision D3 of
 * task F1).
 *
 * <p>A fixed enum, not a table, because the summary of task F2 needs to name a category to say
 * things like "payroll took 31% of the revenue". A category registered by the operator would have
 * to carry a flag saying which row is payroll, and the model would be half enum, half table. The
 * cost accepted is that a new category needs a migration.
 */
public enum ExpenseCategory {

    /** What was paid to whoever works here. Names the employee; see {@link #namesAnEmployee()}. */
    PAYROLL,

    /** Goods and services bought to operate: food, drink, cleaning, laundry. */
    SUPPLIER,

    /** The rent of the building. */
    RENT,

    /** Power, water, gas, internet, telephone. */
    UTILITIES,

    /** Taxes and public fees. */
    TAX,

    /** Repairs and upkeep of the building and the equipment. */
    MAINTENANCE,

    /** Money the owners took out, which is not a cost of operating. */
    WITHDRAWAL,

    /** Anything the categories above do not hold. */
    OTHER;

    /**
     * Whether an expense of this category points at the employee it paid. Only payroll does, and
     * the database refuses an employee on anything else.
     */
    public boolean namesAnEmployee() {
        return this == PAYROLL;
    }

    /**
     * Whether this spending is a cost of running the operation, and so weighs on the result of the
     * period. A withdrawal by the owners moves cash but is not a cost: counting it as one would
     * report a loss in a month that was profitable and the owners simply took their money.
     */
    public boolean weighsOnTheResult() {
        return this != WITHDRAWAL;
    }
}
