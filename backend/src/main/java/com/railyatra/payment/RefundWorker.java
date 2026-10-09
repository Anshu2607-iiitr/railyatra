package com.railyatra.payment;
import com.railyatra.booking.CancellationQuoteRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="railyatra.refunds.worker-enabled",havingValue="true",matchIfMissing=true)
public class RefundWorker {
 private final CancellationQuoteRepository quotes;private final RefundService service;private final long leaseSeconds;
 public RefundWorker(CancellationQuoteRepository quotes,RefundService service,@Value("${railyatra.refunds.lease-seconds:180}") long leaseSeconds){this.quotes=quotes;this.service=service;this.leaseSeconds=leaseSeconds;}
 @Scheduled(fixedDelayString="${railyatra.refunds.poll-ms:5000}") public void settle(){for(String id:quotes.pendingIds(Instant.now().minusSeconds(leaseSeconds),PageRequest.of(0,25)))service.process(id);}
}
