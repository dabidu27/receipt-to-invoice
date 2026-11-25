package com.example.receipt_to_invoice;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.receipt_to_invoice.ui.PreviewReceiptDataActivity;

public class ExampleUnitTest {
    @Test
    public void testParseDate_yyyy_mm_dd() {
        PreviewReceiptDataActivity.ParsedDate date = PreviewReceiptDataActivity.parseDate("2024-05-18");
        assertEquals(2024, date.year);
        assertEquals(5, date.month);
        assertEquals(18, date.day);
        assertEquals(4, date.getDatePickerMonth());
    }

    @Test
    public void testParseDate_dd_mm_yyyy() {
        PreviewReceiptDataActivity.ParsedDate date = PreviewReceiptDataActivity.parseDate("18-05-2024");
        assertEquals(2024, date.year);
        assertEquals(5, date.month);
        assertEquals(18, date.day);
        assertEquals(4, date.getDatePickerMonth());
    }

    @Test
    public void testParseDate_withDots() {
        PreviewReceiptDataActivity.ParsedDate dateYMD = PreviewReceiptDataActivity.parseDate("2023.11.25");
        assertEquals(2023, dateYMD.year);
        assertEquals(11, dateYMD.month);
        assertEquals(25, dateYMD.day);

        PreviewReceiptDataActivity.ParsedDate dateDMY = PreviewReceiptDataActivity.parseDate("25.11.2023");
        assertEquals(2023, dateDMY.year);
        assertEquals(11, dateDMY.month);
        assertEquals(25, dateDMY.day);
    }

    @Test
    public void testParseDate_nullAndEmptyFallback() {
        PreviewReceiptDataActivity.ParsedDate nullDate = PreviewReceiptDataActivity.parseDate(null);
        assertTrue(nullDate.year >= 2024);
        assertTrue(nullDate.month >= 1 && nullDate.month <= 12);
        assertTrue(nullDate.day >= 1 && nullDate.day <= 31);

        PreviewReceiptDataActivity.ParsedDate emptyDate = PreviewReceiptDataActivity.parseDate("");
        assertTrue(emptyDate.year >= 2024);
    }
}