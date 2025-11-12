package br.com.nextjob.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.Reader;
import java.sql.Clob;

@Service
public class DatabaseService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SimpleJdbcCall callInit;
    private SimpleJdbcCall callFnGerarJsonUsuario;
    private SimpleJdbcCall callFnCalcularCompatibilidade;

    @PostConstruct
    public void init() {
        // Procedure: PKG_INICIALIZACAO.PRC_INICIALIZAR_BANCO_NEXTJOB
        this.callInit = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_INICIALIZACAO")
                .withProcedureName("PRC_INICIALIZAR_BANCO_NEXTJOB");

        // Function: PKG_USUARIO.FN_GERAR_JSON_USUARIO
        this.callFnGerarJsonUsuario = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_USUARIO")
                .withFunctionName("FN_GERAR_JSON_USUARIO");

        // Function: PKG_VAGA.FN_CALCULAR_COMPATIBILIDADE
        this.callFnCalcularCompatibilidade = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_VAGA")
                .withFunctionName("FN_CALCULAR_COMPATIBILIDADE");
    }

    /**
     * Limpa todos os dados das tabelas (mantém a estrutura)
     */
    public String limparDados() {
        try {
            // Ordem correta para respeitar constraints de FK
            jdbcTemplate.execute("DELETE FROM recomendacao");
            jdbcTemplate.execute("DELETE FROM vaga_competencia");
            jdbcTemplate.execute("DELETE FROM usuario_competencia");
            jdbcTemplate.execute("DELETE FROM vaga");
            jdbcTemplate.execute("DELETE FROM curso");
            jdbcTemplate.execute("DELETE FROM competencia");
            jdbcTemplate.execute("DELETE FROM empresa");
            jdbcTemplate.execute("DELETE FROM usuario");
            jdbcTemplate.execute("DELETE FROM auditoria");
            jdbcTemplate.execute("COMMIT");
            
            return "✅ Dados limpos com sucesso!";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Erro ao limpar dados: " + e.getMessage();
        }
    }

    /**
     * Inicializa o banco com dados de exemplo
     */
    public String inicializarBanco() {
        try {
            callInit.execute();
            return "✅ Procedure PKG_INICIALIZACAO.PRC_INICIALIZAR_BANCO_NEXTJOB executada com sucesso.";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Erro ao executar inicialização: " + e.getMessage();
        }
    }

    /**
     * Limpa e reinicializa o banco (reset completo)
     */
    public String resetarBanco() {
        try {
            // Primeiro limpa
            limparDados();
            
            // Depois inicializa
            callInit.execute();
            
            return "✅ Banco resetado e reinicializado com sucesso!";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Erro ao resetar banco: " + e.getMessage();
        }
    }

    public String gerarJsonUsuario(int idUsuario) {
        try {
            Object result = callFnGerarJsonUsuario.executeFunction(Object.class, idUsuario);

            if (result == null) {
                return "{}";
            }

            // Se o Oracle retornou um CLOB, converte para String
            if (result instanceof Clob clob) {
                try (Reader reader = clob.getCharacterStream();
                     BufferedReader br = new BufferedReader(reader)) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }

            // Se já veio como String
            return result.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    public String calcularCompatibilidade(int idUsuario, int idVaga) {
        try {
            Object result = callFnCalcularCompatibilidade.executeFunction(Object.class, idUsuario, idVaga);

            if (result instanceof Clob clob) {
                try (Reader reader = clob.getCharacterStream();
                     BufferedReader br = new BufferedReader(reader)) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }

            return result == null ? "{}" : result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
