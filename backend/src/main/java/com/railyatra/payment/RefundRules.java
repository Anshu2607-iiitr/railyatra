package com.railyatra.payment;
import java.math.BigDecimal;
import java.util.Map;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Configurable development policy; never advertised as official railway rules. */
@Component @Validated @ConfigurationProperties(prefix="railyatra.refunds.rules")
public class RefundRules {
 @NotNull @DecimalMin("0") private BigDecimal confirmedCharge=new BigDecimal("60.00");
 @NotNull @DecimalMin("0") private BigDecimal racCharge=new BigDecimal("20.00");
 @NotNull @DecimalMin("0") private BigDecimal waitlistCharge=BigDecimal.ZERO;
 @Min(1) private long advanceHours=48;
 @Min(0) private long nearHours=12;
 @NotNull @DecimalMin("0") @DecimalMax("1") private BigDecimal intermediateRate=new BigDecimal("0.25");
 @NotNull @DecimalMin("0") @DecimalMax("1") private BigDecimal nearRate=new BigDecimal("0.50");
 @NotNull private Map<@NotBlank String,@NotNull @DecimalMin("0") BigDecimal> classCharges=Map.of();
 @AssertTrue(message="advance-hours must be greater than near-hours") public boolean isValidWindows(){return advanceHours>nearHours;}
 public BigDecimal getConfirmedCharge(){return confirmedCharge;}public void setConfirmedCharge(BigDecimal v){confirmedCharge=v;}
 public BigDecimal getRacCharge(){return racCharge;}public void setRacCharge(BigDecimal v){racCharge=v;}
 public BigDecimal getWaitlistCharge(){return waitlistCharge;}public void setWaitlistCharge(BigDecimal v){waitlistCharge=v;}
 public long getAdvanceHours(){return advanceHours;}public void setAdvanceHours(long v){advanceHours=v;}
 public long getNearHours(){return nearHours;}public void setNearHours(long v){nearHours=v;}
 public BigDecimal getIntermediateRate(){return intermediateRate;}public void setIntermediateRate(BigDecimal v){intermediateRate=v;}
 public BigDecimal getNearRate(){return nearRate;}public void setNearRate(BigDecimal v){nearRate=v;}
 public Map<String,BigDecimal> getClassCharges(){return classCharges;}public void setClassCharges(Map<String,BigDecimal> v){classCharges=v;}
}
