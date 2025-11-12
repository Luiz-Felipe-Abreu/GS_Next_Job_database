
package br.com.nextjob.controller;

import br.com.nextjob.service.DatabaseService;
import br.com.nextjob.service.MongoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private DatabaseService dbService;

    @Autowired
    private MongoService mongoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== ENDPOINTS DE INICIALIZAÇÃO ====================
    
    @GetMapping("/inicializar")
    public ResponseEntity<String> inicializarBanco() {
        String resultado = dbService.inicializarBanco();
        return ResponseEntity.ok(resultado);
    }
    
    @GetMapping("/limpar")
    public ResponseEntity<String> limparDados() {
        String resultado = dbService.limparDados();
        return ResponseEntity.ok(resultado);
    }
    
    @GetMapping("/resetar")
    public ResponseEntity<String> resetarBanco() {
        String resultado = dbService.resetarBanco();
        return ResponseEntity.ok(resultado);
    }
    
    // ==================== ENDPOINTS DE CONSULTA ====================

    @GetMapping("/usuario/{id}/json")
    public ResponseEntity<String> usuarioJson(@PathVariable("id") int id) {
        String json = dbService.gerarJsonUsuario(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/compatibilidade")
    public ResponseEntity<String> compatibilidade(@RequestParam("usuario") int usuario,
                                                  @RequestParam("vaga") int vaga) {
        String json = dbService.calcularCompatibilidade(usuario, vaga);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/export-json")
    public ResponseEntity<String> exportJson() throws Exception {
        // Apenas exporta dados do Oracle para arquivos JSON
        return exportToMongo();
    }

    @GetMapping("/import-mongo")
    public ResponseEntity<String> importMongo() throws Exception {
        // Importa os arquivos JSON para o MongoDB
        String resultado = mongoService.importarTudo();
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/processar-tudo")
    public ResponseEntity<String> processarTudo() throws Exception {
        // Exporta do Oracle e importa no MongoDB
        exportToMongo();
        String resultado = mongoService.importarTudo();
        return ResponseEntity.ok("✅ Processo completo executado!\n\n" + resultado);
    }

    @GetMapping("/export/mongo")
    public ResponseEntity<String> exportToMongo() throws Exception {
        // Exporta dados do Oracle para arquivos JSON prontos para MongoDB
        
        // SQL que chama a função PL/SQL para gerar JSON de cada usuário
        String sqlUsuarios = "SELECT pkg_usuario.fn_gerar_json_usuario(id_usuario) AS json FROM usuario ORDER BY id_usuario";
        
        // Exporta usuários
        String usuarios = exportarTabela(sqlUsuarios);
        FileWriter fw = new FileWriter("usuarios.json");
        fw.write(usuarios);
        fw.close();

        // Para outras tabelas, fazemos SELECT direto e convertemos
        exportarTabelaSimples("empresa", "empresas.json");
        exportarTabelaSimples("vaga", "vagas.json");
        exportarTabelaSimples("competencia", "competencias.json");
        exportarTabelaSimples("curso", "cursos.json");
        exportarTabelaSimples("recomendacao", "recomendacoes.json");

        String msg = "✅ Exportação concluída! Arquivos JSON criados:\n" +
                     "- usuarios.json\n" +
                     "- empresas.json\n" +
                     "- vagas.json\n" +
                     "- competencias.json\n" +
                     "- cursos.json\n" +
                     "- recomendacoes.json\n\n" +
                     "Use mongoimport --jsonArray para importar no MongoDB.";
        
        return ResponseEntity.ok(msg);
    }

    /**
     * Exporta dados usando função PL/SQL que retorna JSON (como no SmartLocation)
     */
    private String exportarTabela(String sql) throws Exception {
        StringBuilder jsonArray = new StringBuilder("[");
        boolean first = true;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        
        for (Map<String, Object> row : results) {
            if (!first) jsonArray.append(",");
            
            Object json = row.get("json");
            String jsonStr = clobToString(json);
            
            if (jsonStr != null && !jsonStr.isEmpty()) {
                jsonArray.append(jsonStr);
            } else {
                jsonArray.append("null");
            }
            first = false;
        }
        
        jsonArray.append("]");
        return jsonArray.toString();
    }

    /**
     * Exporta tabelas simples fazendo SELECT direto (sem função PL/SQL)
     */
    private void exportarTabelaSimples(String nomeTabela, String nomeArquivo) throws Exception {
        String sql = "SELECT * FROM " + nomeTabela + " ORDER BY 1";
        List<Map<String, Object>> dados = queryForList(sql);
        
        FileWriter fw = new FileWriter(nomeArquivo);
        mapper.writeValue(fw, dados);
        fw.close();
    }

    /**
     * Converte CLOB do Oracle em String para evitar problemas de serialização JSON
     */
    private String clobToString(Object obj) {
        if (obj instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception e) {
                return "";
            }
        }
        return obj != null ? obj.toString() : "";
    }

    private List<Map<String,Object>> queryForList(String sql) {
        return jdbcTemplate.query(sql, (ResultSet rs) -> {
            List<Map<String,Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                int cols = rs.getMetaData().getColumnCount();
                for (int i=1;i<=cols;i++) {
                    String colName = rs.getMetaData().getColumnLabel(i);
                    Object val = rs.getObject(i);
                    // Converte CLOBs em String antes de adicionar ao mapa
                    row.put(colName.toLowerCase(), clobToString(val));
                }
                list.add(row);
            }
            return list;
        });
    }
}
