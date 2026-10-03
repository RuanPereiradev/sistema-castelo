/**
 * The expense and the rules of the financial result.
 *
 * <p>Two dates on every expense, because profit and cash flow are different numbers (decision D1
 * of task F1): {@code accrualDate} says which month it belongs to, {@code paidAt} says when the
 * money left. An expense without {@code paidAt} is an account payable.
 */
package br.com.castel.finance.domain;
