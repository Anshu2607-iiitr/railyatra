package com.railyatra.payment;

import com.railyatra.booking.PaymentTransaction;
import com.railyatra.booking.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistent simulated gateway receipts survive restarts and lease recovery. No actual funds move. */
@Service
public class MockRefundSettlementService {
 private final PaymentTransactionRepository payments;private final JdbcTemplate jdbc;
 public MockRefundSettlementService(PaymentTransactionRepository payments,JdbcTemplate jdbc){this.payments=payments;this.jdbc=jdbc;}
 @Transactional
 public PaymentProvider.RefundResult refund(PaymentProvider.RefundCommand command){
  if(command.idempotencyKey()==null||command.idempotencyKey().isBlank()||command.idempotencyKey().length()>36||command.amount()==null||command.amount().signum()<=0)return failed(command);
  PaymentTransaction payment=payments.lockByProviderPaymentId(command.providerPaymentId()).orElse(null);
  if(payment==null||!"MOCK".equals(payment.getProviderType()))return failed(command);
  List<Receipt> previous=jdbc.query("select payment_id,amount from mock_refund_receipts where idempotency_key=?",(rs,index)->new Receipt(rs.getString(1),rs.getBigDecimal(2)),command.idempotencyKey());
  if(!previous.isEmpty())return previous.getFirst().paymentId().equals(command.providerPaymentId())&&previous.getFirst().amount().compareTo(command.amount())==0?new PaymentProvider.RefundResult("MOCK",command.providerPaymentId(),PaymentStatus.REFUNDED,command.amount()):failed(command);
  if(!List.of(PaymentStatus.SUCCESS,PaymentStatus.PARTIALLY_REFUNDED).contains(payment.getStatus()))return failed(command);
  BigDecimal settled=jdbc.queryForObject("select coalesce(sum(amount),0) from mock_refund_receipts where payment_id=?",BigDecimal.class,command.providerPaymentId());
  if(settled.add(command.amount()).compareTo(payment.getAmount())>0)return failed(command);
  jdbc.update("insert into mock_refund_receipts(idempotency_key,payment_id,amount,created_at) values(?,?,?,?)",command.idempotencyKey(),command.providerPaymentId(),command.amount(),java.sql.Timestamp.from(Instant.now()));
  return new PaymentProvider.RefundResult("MOCK",command.providerPaymentId(),PaymentStatus.REFUNDED,command.amount());
 }
 private PaymentProvider.RefundResult failed(PaymentProvider.RefundCommand c){return new PaymentProvider.RefundResult("MOCK",c.providerPaymentId(),PaymentStatus.FAILED,BigDecimal.ZERO);}
 private record Receipt(String paymentId,BigDecimal amount){}
}
