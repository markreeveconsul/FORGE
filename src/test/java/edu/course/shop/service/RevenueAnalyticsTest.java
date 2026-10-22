package edu.course.shop.service;

import edu.course.shop.persistence.AdminRepository.OrderRow;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static edu.course.shop.service.RevenueAnalytics.Metric.*;
import static org.junit.jupiter.api.Assertions.*;

class RevenueAnalyticsTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-10-22T02:00:00Z"),ZoneId.of("Asia/Singapore"));
    final LocalDate today=LocalDate.of(2026,10,22);
    OrderRow order(long id,String date,String status,long cents){return new OrderRow(id,"Demo",date,"Demo",status,99,cents);}
    @Test void totalsRemainExactToTheCentAndUnpaidOrdersAreSeparate(){
        var rows=List.of(order(1,"2026-10-21T16:00:00Z","PAID",10),order(2,"2026-10-21T17:00:00Z"," paid ",20),order(3,"2026-10-21T18:00:00Z","PENDING",500),order(4,"2026-10-21T18:00:00Z","REFUNDED",100));
        var report=RevenueAnalytics.calculate(rows,today.minusDays(6),today,clock);
        assertEquals(30,report.current().cents(PAID_REVENUE));assertEquals(630,report.current().cents(ORDER_VALUE));assertEquals(2,report.current().count(PAID_REVENUE));assertEquals(new BigDecimal("0.15"),report.current().average(PAID_REVENUE));
        assertEquals(7,report.current().days().size());assertEquals(0,report.current().days().getFirst().cents(PAID_REVENUE));assertEquals(4,report.current().days().getLast().orders().size());
    }
    @Test void midnightUsesTheReportTimezoneAndFutureOrdersAreExcluded(){
        var rows=List.of(order(1,"2026-10-21T15:59:59Z","PAID",100),order(2,"2026-10-21T16:00:00Z","PAID",200),order(3,"2026-10-22T02:00:01Z","PAID",999));
        var report=RevenueAnalytics.calculate(rows,today,today,clock);
        assertEquals(200,report.current().cents(PAID_REVENUE));assertEquals(100,report.previous().cents(PAID_REVENUE));assertEquals(1,report.futureRecords());assertEquals(ZoneId.of("Asia/Singapore"),report.zone());
    }
    @Test void comparisonsUseAnAdjacentEqualLengthCalendarPeriod(){
        var leapClock=Clock.fixed(Instant.parse("2024-03-02T01:00:00Z"),ZoneOffset.UTC);
        var report=RevenueAnalytics.calculate(List.of(),LocalDate.of(2024,2,28),LocalDate.of(2024,3,1),leapClock);
        assertEquals(List.of(LocalDate.of(2024,2,28),LocalDate.of(2024,2,29),LocalDate.of(2024,3,1)),report.current().days().stream().map(RevenueAnalytics.Day::date).toList());
        assertEquals(LocalDate.of(2024,2,25),report.previous().start());assertEquals(LocalDate.of(2024,2,27),report.previous().end());
    }
    @Test void aZeroPreviousPeriodDoesNotInventAnInfiniteGrowthPercentage(){
        var report=RevenueAnalytics.calculate(List.of(order(1,"2026-10-21T16:00:00Z","PAID",100)),today,today,clock);
        assertTrue(report.changePercent(PAID_REVENUE).isEmpty());
        var empty=RevenueAnalytics.calculate(List.of(),today,today,clock);assertTrue(empty.changePercent(PAID_REVENUE).isEmpty());assertEquals(new BigDecimal("0.00"),empty.current().average(PAID_REVENUE));
    }
    @Test void growthAndLossUseExactRecordedTotals(){
        var rows=List.of(order(1,"2026-10-20T16:00:00Z","PAID",150),order(2,"2026-10-21T16:00:00Z","PAID",200));
        assertEquals(new BigDecimal("33.3"),RevenueAnalytics.calculate(rows,today,today,clock).changePercent(PAID_REVENUE).orElseThrow());
        assertEquals(new BigDecimal("-100.0"),RevenueAnalytics.calculate(rows.subList(0,1),today,today,clock).changePercent(PAID_REVENUE).orElseThrow());
    }
    @Test void invalidRecordsAreCountedAndNeverBecomeZeroDateOrNegativeSales(){
        var report=RevenueAnalytics.calculate(Arrays.asList(order(1,"not-a-date","PAID",100),order(2,"2026-10-21T16:00:00Z","PAID",-1),null),today,today,clock);
        assertEquals(3,report.invalidRecords());assertEquals(0,report.current().cents(PAID_REVENUE));
    }
    @Test void rangeValidationRejectsFutureReversedAndExcessiveSelections(){
        assertThrows(IllegalArgumentException.class,()->RevenueAnalytics.calculate(List.of(),today,today.plusDays(1),clock));
        assertThrows(IllegalArgumentException.class,()->RevenueAnalytics.calculate(List.of(),today,today.minusDays(1),clock));
        assertThrows(IllegalArgumentException.class,()->RevenueAnalytics.calculate(List.of(),null,today,clock));
        assertThrows(IllegalArgumentException.class,()->RevenueAnalytics.calculate(List.of(),today.minusDays(366),today,clock));
        assertEquals(366,RevenueAnalytics.calculate(List.of(),today.minusDays(365),today,clock).current().days().size());
    }
    @Test void drilldownIsImmutableAndCountsOrdersRatherThanLineItemQuantities(){
        var report=RevenueAnalytics.calculate(List.of(order(7,"2026-10-21T16:00:00Z","PAID",100)),today,today,clock);
        var day=report.current().days().getFirst();assertEquals(1,day.count(PAID_REVENUE));assertEquals(100,day.cents(PAID_REVENUE));assertThrows(UnsupportedOperationException.class,()->day.orders().clear());
    }
    @Test void currencyOverflowIsDetectedInsteadOfWrappingIntoNegativeRevenue(){
        var report=RevenueAnalytics.calculate(List.of(order(1,"2026-10-21T16:00:00Z","PAID",Long.MAX_VALUE),order(2,"2026-10-21T16:00:00Z","PAID",1)),today,today,clock);
        assertThrows(ArithmeticException.class,()->report.current().cents(PAID_REVENUE));
    }
}
