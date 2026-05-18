package br.com.carrefour.lancamentos;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca classes ou métodos excluídos da cobertura JaCoCo por decisão de design.
 *
 * <p>Use quando o componente não pode ser testado com as ferramentas disponíveis
 * (Spring AOP proxy, WireMock, contexto RabbitMQ) e o custo de criar a infraestrutura
 * de teste não é proporcional ao risco do código.</p>
 *
 * <p>A exclusão é configurada em {@code build.gradle} — esta anotação documenta
 * o porquê no próprio código.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Documented
public @interface NaoTestablePorDesign {
    String motivo();
}
