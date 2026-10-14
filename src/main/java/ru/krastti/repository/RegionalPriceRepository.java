package ru.krastti.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.krastti.entity.RegionalPriceEntity;
import ru.krastti.entity.RegionalPriceId;
import ru.krastti.entity.PersistenceValues.Frequency;
import ru.krastti.entity.dto.Availability;
import ru.krastti.entity.dto.Fuel;
import ru.krastti.entity.dto.Region;

public interface RegionalPriceRepository extends JpaRepository<RegionalPriceEntity, RegionalPriceId> {
    List<RegionalPriceEntity> findAllByIdSourceId(String sourceId);

    @Query("select distinct p.frequency from RegionalPriceEntity p where p.id.sourceId = :source")
    List<Frequency> findFrequencies(@Param("source") String source);

    @Query("""
        select new ru.krastti.entity.dto.Availability(
            p.id.regionCode, p.id.fuelCode, min(p.id.observedOn), max(p.id.observedOn))
        from RegionalPriceEntity p where p.id.sourceId = :source
        group by p.id.regionCode, p.id.fuelCode order by p.id.regionCode, p.id.fuelCode
        """)
    List<Availability> findAvailability(@Param("source") String source);

    @Query("""
        select distinct new ru.krastti.entity.dto.Region(r.code, r.name)
        from RegionalPriceEntity p join p.region r where p.id.sourceId = :source order by r.code
        """)
    List<Region> findRegions(@Param("source") String source);

    @Query("""
        select distinct new ru.krastti.entity.dto.Fuel(f.code, f.name)
        from RegionalPriceEntity p join p.fuel f where p.id.sourceId = :source order by f.code
        """)
    List<Fuel> findFuels(@Param("source") String source);

    boolean existsByIdSourceIdAndIdRegionCodeAndIdFuelCode(String source, String region, String fuel);

    /** Только опубликованные значения; справочники и ленивые связи не загружаются. */
    interface PriceObservation {
        LocalDate getDate();
        BigDecimal getPrice();
        String getSourceUrl();
    }

    @Query("""
        select p.id.observedOn as date, p.price as price, p.sourceUrl as sourceUrl
        from RegionalPriceEntity p
        where p.id.sourceId = :source and p.id.regionCode = :region and p.id.fuelCode = :fuel
            and p.id.observedOn between :from and :to
        order by p.id.observedOn
        """)
    List<PriceObservation> findPoints(@Param("source") String source, @Param("region") String region,
                                    @Param("fuel") String fuel, @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
