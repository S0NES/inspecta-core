package br.com.vistoria.core.config;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantTransactionAspect {

    private final JdbcTemplate jdbc;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenant(ProceedingJoinPoint pjp) throws Throwable {
        java.util.List<java.util.UUID> tenants = TenantContext.getAll();
        if (!tenants.isEmpty()) {
            // SET LOCAL não aceita bind params; SELECT set_config aceita.
            // app.current_tenants (V26): CSV com todos os vínculos — a RLS filtra por ANY().
            // app.current_tenant (legado): 1ª imobiliária, para policies ainda não migradas.
            String csv = tenants.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            jdbc.queryForObject("SELECT set_config('app.current_tenants', ?, true)", String.class, csv);
            jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenants.get(0).toString());
        }
        return pjp.proceed();
    }
}
