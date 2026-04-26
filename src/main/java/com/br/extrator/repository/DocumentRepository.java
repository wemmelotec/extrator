package com.br.extrator.repository;

import com.br.extrator.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório para acesso e persistência de documentos
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
}
