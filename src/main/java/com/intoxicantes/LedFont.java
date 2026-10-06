package com.intoxicantes;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FONTE DE LED 5×7 (v1.2.24) — bitmap desenhado aqui, NADA de fonte do
 * Minecraft no letreiro. Cada linha é um array de 7 bytes (1 por pixel de
 * altura); cada byte tem os 5 bits de LARGURA (bit 4 = coluna esquerda).
 *
 * v1.2.24 — CORREÇÃO DO CRASH: o mapa antigo era um array indexado por
 * `CARACTERES.indexOf(c)` e tinha UMA linha a mais num dos glifos, atrasando
 * todos os seguintes — "Ã" (de ESQUINÃO) pedia índice 46 num array de 46 e
 * estourou o render frame inteiro. Agora o mapa é {@code Map<Character, glifo>}
 * construído em pares (letra, bitmap): impossível desafasar, e
 * {@link #glifo(char)} NUNCA sai dos bounds (fallback = espaço em branco).
 *
 * O renderer lê {@link #glifo(char)} e desenha cada pixel aceso como um quad
 * brilhante — cara de painel de LED de posto de gasolina: bloco, quadrada,
 * sem anti-aliasing. {@link #largura(String)} mede a linha em pixels do grid
 * (5 por letra + 1 de espaço). Acentos: 'Ã'→A, 'Ç'→C, 'É'→E... o LED de
 * esquina não desenha til, mas lê português.
 */
public final class LedFont {

    private LedFont() {}

    /** Largura do glifo (px) + espaço entre letras. */
    public static final int LARGURA_GLYPH = 5;
    public static final int ALTURA_GLYPH = 7;
    public static final int ESPACO = 1;

    /** Passo horizontal entre letras (glifo + espaço) = 6px. */
    public static final int GLIFO_ESPACO = LARGURA_GLYPH + ESPACO;
    /** Respiro vertical entre linhas do letreiro (px da fonte) — v1.2.25:
     * 2 linhas de 7px + 2 de respiro = 16px exatos: a faixa do painel inteiro. */
    public static final int LINHA_VAZIO = 2;

    /** glifo nulo — usado quando um caractere não tem desenho (nunca deve falhar). */
    private static final byte[] VAZIO = {
            0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000};

    /**
     * O alfabeto: cada glifo é exatamente 7 bytes (1 por linha), 5 bits de
     * largura cada. Mapeado por Character — sem índice, sem desafasamento.
     */
    private static final Map<Character, byte[]> FONTES = new HashMap<>();

    private static void add(char c, int... linhas) {
        if (linhas.length != ALTURA_GLYPH)
            throw new IllegalStateException(
                    "Glifo '" + c + "' tem " + linhas.length + " linhas (esperado " + ALTURA_GLYPH + ")");
        byte[] bs = new byte[ALTURA_GLYPH];
        for (int i = 0; i < ALTURA_GLYPH; i++) bs[i] = (byte) linhas[i];
        FONTES.put(c, bs);
    }

    static {
        // ---- letras
        add('A', 0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001);
        add('B', 0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110);
        add('C', 0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110);
        add('D', 0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110);
        add('E', 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111);
        add('F', 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000);
        add('G', 0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110);
        add('H', 0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001);
        add('I', 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111);
        add('J', 0b00111, 0b00010, 0b00010, 0b00010, 0b00010, 0b10010, 0b01100);
        add('K', 0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001);
        add('L', 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111);
        add('M', 0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001);
        add('N', 0b10001, 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001);
        add('O', 0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110);
        add('P', 0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000);
        add('Q', 0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10011, 0b01101);
        add('R', 0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001);
        add('S', 0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110);
        add('T', 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100);
        add('U', 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110);
        add('V', 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100);
        add('W', 0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001);
        add('X', 0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001);
        add('Y', 0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100);
        add('Z', 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111);
        // ---- números
        add('0', 0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110);
        add('1', 0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110);
        add('2', 0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111);
        add('3', 0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110);
        add('4', 0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010);
        add('5', 0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110);
        add('6', 0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110);
        add('7', 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000);
        add('8', 0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110);
        add('9', 0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100);
        // ---- símbolos (o comércio!)
        add('&', 0b01100, 0b10010, 0b10010, 0b01100, 0b10101, 0b10010, 0b01101);
        add('.', 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100);
        add(',', 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b00100, 0b01000);
        add('!', 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00000, 0b00100);
        add('?', 0b01110, 0b10001, 0b00001, 0b00110, 0b00100, 0b00000, 0b00100);
        add('-', 0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000);
        add(':', 0b00000, 0b01100, 0b01100, 0b00000, 0b01100, 0b01100, 0b00000);
        add('$', 0b00100, 0b01111, 0b10100, 0b01110, 0b00101, 0b11110, 0b00100);
        add('/', 0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000);
        add('\'', 0b00100, 0b00100, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000);
        add('+', 0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000);
        add('*', 0b00000, 0b10101, 0b01110, 0b11111, 0b01110, 0b10101, 0b00000);
        add('°', 0b01100, 0b01100, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000);
        add('%', 0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011);
        // ---- espaço (e fallback)
        add(' ', 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000);
    }

    /** Normalização: acentos caem na letra base (o LED não desenha til). */
    private static char normalizar(char c) {
        return switch (c) {
            case 'Á', 'À', 'Â', 'Ã', 'Ä' -> 'A';
            case 'á', 'à', 'â', 'ã', 'ä' -> 'A';
            case 'É', 'È', 'Ê', 'Ë' -> 'E';
            case 'é', 'è', 'ê', 'ë' -> 'E';
            case 'Í', 'Ì', 'Î', 'Ï' -> 'I';
            case 'í', 'ì', 'î', 'ï' -> 'I';
            case 'Ó', 'Ò', 'Ô', 'Õ', 'Ö' -> 'O';
            case 'ó', 'ò', 'ô', 'õ', 'ö' -> 'O';
            case 'Ú', 'Ù', 'Û', 'Ü' -> 'U';
            case 'ú', 'ù', 'û', 'ü' -> 'U';
            case 'Ç', 'ç' -> 'C';
            case 'Ñ', 'ñ' -> 'N';
            default -> Character.toUpperCase(c);
        };
    }

    /** Glifo do caractere (sempre devolve 7 bytes — nunca NPE, nunca OOB). */
    public static byte[] glifo(char c) {
        byte[] g = FONTES.get(normalizar(c));
        return g != null ? g : VAZIO;
    }

    /** true se o caractere tem desenho próprio (≠ fallback em branco). */
    public static boolean temGlifo(char c) {
        return FONTES.containsKey(normalizar(c));
    }

    /** Largura da string em pixels do grid (letras + espaços, sem borda). */
    public static int largura(String texto) {
        if (texto == null || texto.isEmpty()) return 0;
        int px = 0;
        for (int i = 0; i < texto.length(); i++) {
            px += temGlifo(texto.charAt(i)) ? LARGURA_GLYPH : ESPACO; // desconhecido = espaço fino
            if (i < texto.length() - 1) px += ESPACO;
        }
        return px;
    }

    /**
     * ESCALA que cabe (v1.2.25): o maior fator de escala UNIFORME (float) que
     * desenha {@code linhas} dentro de {@code larguraMaxPx} × {@code alturaMaxPx}
     * — sem esticar, sem cortar, e NUNCA acima de 1.0 (letra no máximo 7px de
     * painel; nada de gigantismo).
     *
     * v1.2.25 — CORREÇÃO DE UNIDADE: as duas medidas de ENTRADA já são em px
     * do grid da fonte (largura conta glifo+espaço; altura conta glifo+vazio).
     * Dividir pela {@link #GLIFO_ESPACO} (6, um passo HORIZONTAL) na altura
     * espremia o texto pra escala ~0.06 — os "tracinhos miúdos" do letreiro.
     *
     * Sem linhas (lista vazia), devolve 1.0.
     */
    public static float escalaPara(List<String> linhas, int larguraMaxPx, int alturaMaxPx) {
        if (linhas == null || linhas.isEmpty() || larguraMaxPx <= 0 || alturaMaxPx <= 0) {
            return 1.0F;
        }
        int w = larguraMax(linhas);
        if (w <= 0) return 1.0F;
        int n = linhas.size();
        int alturaFonte = n * ALTURA_GLYPH + (n - 1) * LINHA_VAZIO;
        float escalaLargura = (float) larguraMaxPx / w;
        float escalaAltura = (float) alturaMaxPx / alturaFonte;
        return Math.min(1.0F, Math.min(escalaLargura, escalaAltura));
    }

    /** Texto mais largo que cabe numa largura (pro renderer calcular escala). */
    public static int larguraMax(List<String> linhas) {
        int maior = 0;
        for (String l : linhas) maior = Math.max(maior, largura(l));
        return maior;
    }

    /** Quebra um texto longo no espaço mais próximo (o letreiro tem 4 linhas). */
    public static String[] quebrar(String texto, int maxPorLinha) {
        texto = texto == null ? "" : texto.trim();
        if (texto.length() <= maxPorLinha) return new String[]{texto};
        int corte = texto.lastIndexOf(' ', maxPorLinha);
        if (corte <= 0) corte = maxPorLinha;
        String a = texto.substring(0, corte).trim();
        String b = texto.substring(corte).trim();
        if (b.length() > maxPorLinha) b = b.substring(0, maxPorLinha); // trunca
        return new String[]{a, b};
    }

    /** Sequência formatada (o renderer consome FormattedCharSequence). */
    public static FormattedCharSequence seq(String texto) {
        return Component.literal(texto == null ? "" : texto).getVisualOrderText();
    }

    /** Linhas formatadas prontas pro renderer. */
    public static List<FormattedCharSequence> seqs(List<String> linhas) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String l : linhas) out.add(seq(l));
        return out;
    }
}
