package br.com.nextjob.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.Reader;

@Service
public class MongoService {

    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DB_NAME = "nextjob";

    /**
     * Importa todos os arquivos JSON para o MongoDB
     */
    public String importarTudo() throws Exception {
        StringBuilder resultado = new StringBuilder();
        
        resultado.append(importarColecao("usuarios.json", "usuarios"));
        resultado.append(importarColecao("empresas.json", "empresas"));
        resultado.append(importarColecao("vagas.json", "vagas"));
        resultado.append(importarColecao("competencias.json", "competencias"));
        resultado.append(importarColecao("cursos.json", "cursos"));
        resultado.append(importarColecao("recomendacoes.json", "recomendacoes"));
        
        return "✅ Importação completa para MongoDB concluída!\n\n" + resultado.toString();
    }

    /**
     * Importa um arquivo JSON específico para uma coleção do MongoDB
     */
    public String importarColecao(String arquivo, String nomeColecao) throws Exception {
        try (MongoClient client = MongoClients.create(MONGO_URI)) {
            MongoDatabase db = client.getDatabase(DB_NAME);
            MongoCollection<Document> col = db.getCollection(nomeColecao);

            // Limpa a coleção antes de importar
            col.drop();

            // Lê o arquivo JSON
            Reader reader = new FileReader(arquivo);
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            reader.close();

            // Insere cada documento na coleção
            int count = 0;
            for (JsonElement e : array) {
                Document doc = Document.parse(e.toString());
                col.insertOne(doc);
                count++;
            }

            return String.format("  📄 %s -> %d documentos importados\n", nomeColecao, count);
        } catch (Exception e) {
            return String.format("  ❌ Erro ao importar %s: %s\n", nomeColecao, e.getMessage());
        }
    }
}
