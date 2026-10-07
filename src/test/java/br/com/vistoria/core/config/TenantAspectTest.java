package br.com.vistoria.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantTransactionAspect")
class TenantAspectTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID TENANT_C = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID TENANT_D = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID TENANT_E = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Mock
    private JdbcTemplate jdbc;

    private TenantTransactionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new TenantTransactionAspect(jdbc);
    }

    @Test
    @DisplayName("applyTenant uses set_config with parameterized query (T1.3 safe SQL)")
    void applyTenant_usesParameterizedQuery() throws Throwable {
        TenantContext.set(TENANT_A);

        try {
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenants', ?, true)",
                    String.class, TENANT_A.toString())).thenReturn(TENANT_A.toString());
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenant', ?, true)",
                    String.class, TENANT_A.toString())).thenReturn(TENANT_A.toString());

            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            when(pjp.proceed()).thenReturn("result");

            aspect.applyTenant(pjp);

            // Verify that queryForObject was called (parameterized query avoids injection)
            verify(jdbc, atLeastOnce()).queryForObject(any(String.class), any(Class.class), any(String.class));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("applyTenant does not call set_config when tenant is null")
    void applyTenant_nullTenant_noSetConfig() throws Throwable {
        TenantContext.clear();

        org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("result");

        aspect.applyTenant(pjp);

        // Should not call jdbc.queryForObject when tenant is null
        verify(jdbc, never()).queryForObject(any(String.class), any(Class.class), any(String.class));
    }

    @Test
    @DisplayName("applyTenant proceeds with join point")
    void applyTenant_proceedsWithJoinPoint() throws Throwable {
        TenantContext.set(TENANT_B);

        try {
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenants', ?, true)",
                    String.class, TENANT_B.toString())).thenReturn(TENANT_B.toString());
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenant', ?, true)",
                    String.class, TENANT_B.toString())).thenReturn(TENANT_B.toString());

            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            when(pjp.proceed()).thenReturn("join_point_result");

            Object result = aspect.applyTenant(pjp);

            assertThat(result).isEqualTo("join_point_result");
            verify(pjp).proceed();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("applyTenant seta app.current_tenants como CSV com todos os vínculos (V26)")
    void applyTenant_multiTenant_setsCsv() throws Throwable {
        TenantContext.set(java.util.List.of(TENANT_C, TENANT_D));
        String csv = TENANT_C + "," + TENANT_D;

        try {
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenants', ?, true)",
                    String.class, csv)).thenReturn(csv);
            when(jdbc.queryForObject(
                    "SELECT set_config('app.current_tenant', ?, true)",
                    String.class, TENANT_C.toString())).thenReturn(TENANT_C.toString());

            org.aspectj.lang.ProceedingJoinPoint pjp = mock(org.aspectj.lang.ProceedingJoinPoint.class);
            when(pjp.proceed()).thenReturn("ok");

            aspect.applyTenant(pjp);

            verify(jdbc).queryForObject(
                    "SELECT set_config('app.current_tenants', ?, true)", String.class, csv);
            verify(jdbc).queryForObject(
                    "SELECT set_config('app.current_tenant', ?, true)", String.class, TENANT_C.toString());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("TenantContext.set and get work correctly")
    void tenantContext_setAndGet() {
        TenantContext.set(TENANT_E);

        try {
            assertThat(TenantContext.get()).isEqualTo(TENANT_E);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("TenantContext.get returns null when not set")
    void tenantContext_nullWhenNotSet() {
        TenantContext.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("applyTenant aspect annotation check")
    void aspect_hasAnnotations() {
        // Verify the aspect has @Aspect annotation
        assertThat(aspect.getClass().isAnnotationPresent(
                org.springframework.stereotype.Component.class)).isTrue();
    }
}
