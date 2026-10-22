package edu.course.shop.service;

import edu.course.shop.persistence.AdminRepository.OrderRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Exact, zero-filled daily order analytics. Only plotted coordinates use floating point. */
public final class RevenueAnalytics {
    private RevenueAnalytics() { }
    public enum Metric {
        PAID_REVENUE("Paid revenue"), ORDER_VALUE("All order value");
        private final String label;
        Metric(String label){this.label=label;}
        public String label(){return label;}
    }
    public record Day(LocalDate date,List<OrderRow> orders) {
        public Day { orders=List.copyOf(orders); }
        public List<OrderRow> matching(Metric metric){return orders.stream().filter(o->metric==Metric.ORDER_VALUE||paid(o)).toList();}
        public long cents(Metric metric){long total=0;for(var order:matching(metric))total=Math.addExact(total,order.totalCents());return total;}
        public int count(Metric metric){return matching(metric).size();}
    }
    public record Period(LocalDate start,LocalDate end,List<Day> days) {
        public Period { days=List.copyOf(days); }
        public long cents(Metric metric){long total=0;for(var day:days)total=Math.addExact(total,day.cents(metric));return total;}
        public int count(Metric metric){return days.stream().mapToInt(d->d.count(metric)).sum();}
        public BigDecimal average(Metric metric){int count=count(metric);return count==0?BigDecimal.ZERO.setScale(2):BigDecimal.valueOf(cents(metric),2).divide(BigDecimal.valueOf(count),2,RoundingMode.HALF_UP);}
    }
    public record Report(Period current,Period previous,ZoneId zone,LocalDate today,int invalidRecords,int futureRecords) {
        public Optional<BigDecimal> changePercent(Metric metric){long before=previous.cents(metric);return before==0?Optional.empty():Optional.of(BigDecimal.valueOf(current.cents(metric)).subtract(BigDecimal.valueOf(before)).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(before),1,RoundingMode.HALF_UP));}
    }
    public static Report calculate(List<OrderRow> orders,LocalDate start,LocalDate end,Clock clock){
        Objects.requireNonNull(clock);LocalDate today=LocalDate.now(clock);
        if(start==null||end==null||end.isBefore(start)||end.isAfter(today))throw new IllegalArgumentException("Choose a valid date range ending today or earlier.");
        long days=ChronoUnit.DAYS.between(start,end)+1;
        if(days>366)throw new IllegalArgumentException("Choose at most 366 days at a time.");
        LocalDate previousStart=start.minusDays(days),previousEnd=start.minusDays(1);
        Map<LocalDate,List<OrderRow>> grouped=new HashMap<>();int invalid=0,future=0;Instant now=clock.instant();
        for(OrderRow order:orders){
            if(order==null||order.totalCents()<=0){invalid++;continue;}
            Instant created;try{created=Instant.parse(order.createdAt());}catch(RuntimeException error){invalid++;continue;}
            if(created.isAfter(now)){future++;continue;}
            LocalDate date=created.atZone(clock.getZone()).toLocalDate();
            if(!date.isBefore(previousStart)&&!date.isAfter(end))grouped.computeIfAbsent(date,d->new ArrayList<>()).add(order);
        }
        return new Report(period(start,end,grouped),period(previousStart,previousEnd,grouped),clock.getZone(),today,invalid,future);
    }
    private static Period period(LocalDate start,LocalDate end,Map<LocalDate,List<OrderRow>> grouped){
        List<Day> days=new ArrayList<>();for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1))days.add(new Day(date,grouped.getOrDefault(date,List.of())));
        return new Period(start,end,days);
    }
    private static boolean paid(OrderRow order){return "PAID".equalsIgnoreCase(order.paymentStatus()==null?"":order.paymentStatus().trim());}
}
