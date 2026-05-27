package com.br.extrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuração de armazenamento de arquivos em filesystem
 * Lê propriedades do arquivo application.properties
 */
@Component
@ConfigurationProperties(prefix = "document.storage")
public class DocumentStorageConfig {

    private String basePath = "uploads/documentos";

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    /**
     * Garante que o diretorio base de armazenamento existe no filesystem.
     *
     * @return caminho absoluto e normalizado do diretorio de armazenamento
     */
    public Path ensureStorageDirectory() {
        Path storagePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storagePath);
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível criar o diretório de armazenamento: " + storagePath, e);
        }
        return storagePath;
    }

    /**
     * Gera um caminho unico para o arquivo a partir de um UUID e da extensao.
     *
     * @param nomeOriginal nome original do arquivo enviado pelo usuario
     * @return caminho relativo onde o arquivo sera gravado
     */
    public String generateUniqueFilePath(String nomeOriginal) {
        String uuid = java.util.UUID.randomUUID().toString();
        String extension = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        return basePath + "/" + uuid + extension;
    }
}
