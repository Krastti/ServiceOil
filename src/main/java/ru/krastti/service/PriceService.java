package ru.krastti.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.krastti.exception.*;
import ru.krastti.entity.PersistenceValues.ImportStatus;
import ru.krastti.entity.dto.*;
import ru.krastti.repository.DataSourceRepository;
import ru.krastti.repository.ImportRunRepository;
import ru.krastti.repository.RegionalPriceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
@RequiredArgsConstructor
public class PriceService {

    private final DataSourceRepository sources;
    private final ImportRunRepository imports;
    private final RegionalPriceRepository prices;


    public Catalog catalog(String source) {
        return new Catalog(metadata(source),prices.findRegions(source),prices.findFuels(source),prices.findAvailability(source));
    }
    public History history(String source,String region,String fuel,LocalDate from,LocalDate to) {
        validatePeriod(from,to);
        var meta=metadata(source);
        requireCombination(source,region,fuel);
        return new History(meta,region,fuel,from,to,points(source,region,fuel,from,to));
    }
    public Summary summary(String source,String region,String fuel,LocalDate from,LocalDate to) {
        var h=history(source,region,fuel,from,to);
        var points=h.points();
        if(points.isEmpty()) return new Summary(h.metadata(),region,fuel,from,to,0,null,null,null,null,null);
        var first=points.getFirst(); var last=points.getLast();
        var delta=points.size()<2?null:last.price().subtract(first.price());
        var percent=delta==null?null:delta.multiply(new BigDecimal("100")).divide(first.price(),2,RoundingMode.HALF_UP);
        return new Summary(h.metadata(),region,fuel,from,to,points.size(),last.price(),first.date(),last.date(),delta,percent);
    }
    public Compare compare(String source,String a,String b,String fuel,LocalDate from,LocalDate to) {
        var ha=history(source,a,fuel,from,to); var hb=history(source,b,fuel,from,to);
        var byDate=new HashMap<LocalDate,PricePoint>(); hb.points().forEach(p->byDate.put(p.date(),p));
        var dates=new ArrayList<LocalDate>(); Comparison comparison=null;
        for(var point:ha.points()) {
            var other=byDate.get(point.date());
            if(other!=null) { dates.add(point.date()); comparison=new Comparison(point.date(),point.price(),other.price(),point.price().subtract(other.price())); }
        }
        var warnings=dates.isEmpty()?List.of("Общие даты отсутствуют. Числовое сравнение недоступно."):List.<String>of();
        return new Compare(ha.metadata(),a,b,fuel,from,to,ha.points(),hb.points(),dates,comparison,warnings);
    }
    public Estimate estimate(EstimateRequest request) {
        String source=request.sourceId()==null?"ROSSTAT_PFO":request.sourceId();
        var meta=metadata(source);
        var liters=totalLiters(request);
        var point=exact(source,request.regionId(),request.fuelCode(),request.priceDate());
        var cost=cost(liters,point.price());
        BudgetComparison comparison=null;
        if(request.comparisonRegionId()!=null) {
            var other=exact(source,request.comparisonRegionId(),request.fuelCode(),request.priceDate());
            var otherCost=cost(liters,other.price());
            comparison=new BudgetComparison(request.comparisonRegionId(),other.price(),otherCost,cost.subtract(otherCost));
        }
        return new Estimate(meta,request.regionId(),request.fuelCode(),point.date(),liters,point.price(),cost,point.sourceUrl(),comparison);
    }
    private PricePoint exact(String source,String region,String fuel,LocalDate date) {
        requireCombination(source,region,fuel);
        var points=points(source,region,fuel,date,date);
        if(points.isEmpty()) throw new PriceNotFoundException();
        return points.getFirst();
    }
    public static BigDecimal totalLiters(EstimateRequest request) {
        if((request.volumeLiters()==null)==(request.vehicles()==null)) bad("Укажите либо объём закупки, либо технику.");
        BigDecimal total=request.volumeLiters();
        if(request.vehicles()!=null) {
            if(request.vehicles().isEmpty() || request.vehicles().size()>100) bad("Добавьте от 1 до 100 строк техники.");
            total=BigDecimal.ZERO;
            for(var vehicle:request.vehicles()) {
                if(vehicle.remainingLiters().compareTo(vehicle.tankCapacity())>0) bad("Остаток не может превышать ёмкость бака.");
                total=total.add(vehicle.tankCapacity().subtract(vehicle.remainingLiters()).multiply(BigDecimal.valueOf(vehicle.count())));
            }
        }
        if(total.signum()<=0 || total.compareTo(new BigDecimal("1000000"))>0) bad("Общий объём должен быть больше нуля и не более 1 000 000 литров.");
        return total;
    }
    public static BigDecimal cost(BigDecimal liters,BigDecimal price) { return liters.multiply(price).setScale(2,RoundingMode.HALF_UP); }
    private static void validatePeriod(LocalDate from,LocalDate to) {
        if(from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>=366) bad("Выберите период от 1 до 366 дней: начало не позже конца.");
    }
    private static void bad(String message) { throw new InvalidRequestException(message); }

    private Metadata metadata(String sourceId) {
        var source = sources.findById(sourceId)
                .orElseThrow(() -> new UnknownSourceException());
        // Сохраняем прежний MIN по текстовым значениям, а не порядок enum.
        var frequency = prices.findFrequencies(sourceId).stream().map(Enum::name)
                .min(String::compareTo).orElse(null);
        var warnings = new ArrayList<String>();
        if (source.getDataMode().name().equals("DEMO")) {
            warnings.add("Демонстрационные данные, цены вымышлены.");
        } else {
            warnings.add("Архивные опубликованные данные; цена региона оценивает бюджет и не гарантирует предложение АЗС.");
        }
        if ("MONTHLY".equals(frequency)) {
            warnings.add("Месячные данные на конец периода; дневная история отсутствует.");
        }
        if (imports.findFirstBySourceIdOrderByIdDesc(sourceId)
                .map(run -> run.getStatus() == ImportStatus.FAILED).orElse(false)) {
            warnings.add("Последнее обновление не удалось. Показан ранее сохранённый набор.");
        }
        return new Metadata(source.getId(), source.getName(), source.getDataMode().name(), "LITER", "RUB",
                frequency, source.getMethodology(), source.getLastImportedAt(), warnings);
    }

    private void requireCombination(String source, String region, String fuel) {
        if (!prices.existsByIdSourceIdAndIdRegionCodeAndIdFuelCode(source, region, fuel)) {
            throw new UnknownCombinationException();
        }
    }

    private List<PricePoint> points(String source, String region, String fuel, LocalDate from, LocalDate to) {
        return prices.findPoints(source, region, fuel, from, to).stream()
                .map(point -> new PricePoint(point.getDate(), point.getPrice(), null, null, null, point.getSourceUrl()))
                .toList();
    }
}
