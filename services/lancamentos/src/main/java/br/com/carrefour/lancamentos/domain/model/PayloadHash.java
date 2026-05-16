package br.com.carrefour.lancamentos.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class PayloadHash {

    private PayloadHash() {}

    public static String compute(TipoLancamento tipo, Valor valor, LocalDate dataCompetencia, String descricao) {
        var canonical = tipo.name()
                + "|" + valor.toBigDecimal().setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString()
                + "|" + dataCompetencia
                + "|" + (descricao != null ? descricao.strip() : "");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
