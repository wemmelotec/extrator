# Etapa 2 - Persistência de PDFs e Metadados

## Resumo das Alterações

A Etapa 2 implementa a infraestrutura completa de armazenamento e persistência de documentos PDF com metadados. 

### Componentes Adicionados

#### 1. **Modelo de Dados (JPA Entity)**
- `Document.java` - Entidade que representa um documento armazenado
  - Campos principais: nomeOriginal, caminhoArquivo, textoExtraido, dataUpload, status
  - Metadados: numeroProcesso, materia, numeroAutoInfracao, períodos, identificacaoAutuado
  - Flag textoNativo para indicar se usou extração nativa PDF ou OCR

#### 2. **Status do Documento**
- `StatusDocumento.java` - Enum com estados: UPLOADING → PROCESSING → COMPLETED (ou ERROR)

#### 3. **Persistência (JPA/Hibernate)**
- `DocumentRepository.java` - Spring Data JPA interface com métodos de busca:
  - findByNumeroProcessoContainingIgnoreCase()
  - findByMateriaContainingIgnoreCase()
  - findByNumeroAutoInfracaoContainingIgnoreCase()
  - findByCpfCnpjIeContainingIgnoreCase()
  - findByStatus()

#### 4. **Armazenamento em Filesystem**
- `DocumentStorageConfig.java` - Configuração de caminho de armazenamento:
  - Lê propriedade `document.storage.base-path` de application.properties (default: `uploads/documentos`)
  - Garante criação de diretórios automaticamente
  - Gera UUIDs únicos para cada arquivo

#### 5. **Serviço de Documentos**
- `DocumentService.java` - Orquestra todo fluxo:
  - processarUpload() - Salva arquivo em disco + cria registro no BD
  - processarOCR() - Executa OCR de forma síncrona (base para futuro assíncrono)
  - Métodos de busca: buscarPorProcesso(), buscarPorMateria(), etc.
  - deletarDocumento() - Remove arquivo e registro

#### 6. **API REST**
- `DocumentoController.java` - Novos endpoints em `/api/documentos`:
  - **POST** `/api/documentos` - Upload com persistência
  - **GET** `/api/documentos` - Lista todos documentos
  - **GET** `/api/documentos/{id}` - Busca específica
  - **GET** `/api/documentos/buscar/processo?numeroProcesso=NUM` - Busca por processo
  - **GET** `/api/documentos/buscar/materia?materia=MAT` - Busca por matéria
  - **GET** `/api/documentos/buscar/auto-infracao?numeroAutoInfracao=NUM` - Busca por auto
  - **GET** `/api/documentos/buscar/identificacao?cpfCnpjIe=ID` - Busca por ID do autuado
  - **DELETE** `/api/documentos/{id}` - Deleta documento

#### 7. **DTO para Listagem**
- `DocumentoListaDTO.java` - Retorna informações resumidas (sem texto completo) por performance

#### 8. **Banco de Dados**
- **H2 Database** - Banco em memória para desenvolvimento/testes
  - Acesso web console: http://localhost:8080/h2-console
  - Hibernate DDL-Auto: `update` (auto-cria tabelas)

#### 9. **Endpoint Legado**
- `ExtratorDecisaoController.java` - Mantido para compatibilidade (sem persistência)

---

## Como Testar Etapa 2

### Pré-requisitos
- Java 25 com `--enable-native-access=ALL-UNNAMED`
- Maven 3.x
- PDFs de teste locais
- Tesseract 4.5.3 instalado em `C:\workspace_eclipse` (ou ajustar em application.properties)

### 1. **Iniciar a Aplicação**

```bash
cd c:\workspace_eclipse\extrator\extrator
mvn spring-boot:run
```

Ou, se preferir compilar e rodar o JAR:
```bash
mvn -DskipTests clean package
java --enable-native-access=ALL-UNNAMED -jar target/extrator-0.0.1-SNAPSHOT.jar
```

A aplicação iniciará em `http://localhost:8080`

### 2. **Testar Upload with Persistência (Nova Abordagem)**

#### Usando cURL:
```bash
curl -X POST http://localhost:8080/api/documentos \
  -F "file=@seu_arquivo.pdf" \
  -H "Accept: application/json"
```

**Resposta esperada (201 Created):**
```json
{
  "id": 1,
  "nomeOriginal": "seu_arquivo.pdf",
  "dataUpload": "2026-04-14T14:51:00",
  "dataProcessamento": "2026-04-14T14:51:05",
  "status": "COMPLETED",
  "numeroProcesso": null,
  "materia": null,
  "textoNativo": true,
  "tamanhoTexto": 2345
}
```

#### Usando Postman/Insomnia:
1. Criar nova requisição **POST** para `http://localhost:8080/api/documentos`
2. No Body, selecionar form-data
3. Adicionar campo "file" do tipo File e selecinoar PDF
4. Enviar

### 3. **Listar Todos os Documentos**

```bash
curl http://localhost:8080/api/documentos
```

**Resposta esperada (200 OK):**
```json
[
  {
    "id": 1,
    "nomeOriginal": "decisao_001.pdf",
    "dataUpload": "2026-04-14T14:51:00",
    "dataProcessamento": "2026-04-14T14:51:05",
    "status": "COMPLETED",
    "numeroProcesso": null,
    "materia": null,
    "textoNativo": true,
    "tamanhoTexto": 2345
  },
  {
    "id": 2,
    "nomeOriginal": "decisao_002.pdf",
    "dataUpload": "2026-04-14T14:55:00",
    "dataProcessamento": "2026-04-14T14:55:10",
    "status": "COMPLETED",
    "numeroProcesso": null,
    "materia": null,
    "textoNativo": false,
    "tamanhoTexto": 3456
  }
]
```

### 4. **Buscar por Critérios Específicos**

#### Buscar por Número de Processo:
```bash
curl http://localhost:8080/api/documentos/buscar/processo?numeroProcesso=123456
```

#### Buscar por Matéria:
```bash
curl http://localhost:8080/api/documentos/buscar/materia?materia=ICMS
```

#### Buscar por Auto de Infração:
```bash
curl http://localhost:8080/api/documentos/buscar/auto-infracao?numeroAutoInfracao=789012
```

#### Buscar por CPF/CNPJ/IE do Autuado:
```bash
curl http://localhost:8080/api/documentos/buscar/identificacao?cpfCnpjIe=12345678901234
```

### 5. **Verificar Banco H2**

Acesse http://localhost:8080/h2-console com credenciais:
- **JDBC URL:** `jdbc:h2:mem:testdb`
- **User:** `sa`
- **Password:** (deixar em branco)

Execute SQL para listar documentos:
```sql
SELECT id, nome_original, data_upload, status, texto_nativo, tamanho_texto FROM documento;
```

### 6. **Verificar Arquivos no Filesystem**

Os arquivos são salvos em `uploads/documentos/` (conforme application.properties):
```bash
dir uploads\documentos\
```

Você verá arquivos nomeados com UUID:
```
d3a4b5c6-e7f8-9a0b-c1d2-e3f4a5b6c7d8.pdf
a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d.pdf
```

### 7. **Endpoint Legado Still Funciona (sem BD)**

Para manter compatibilidade, o endpoint antigo ainda funciona:
```bash
curl -X POST http://localhost:8080/extrair/decisao \
  -F "file=@seu_arquivo.pdf"
```

Retorna apenas o texto extraído (sem salvar em BD).

---

## Estrutura de Arquivos Criada

```
src/main/java/com/br/extrator/
├── model/
│   ├── Document.java              (Entidade JPA)
│   └── StatusDocumento.java       (Enum de status)
├── repository/
│   └── DocumentRepository.java    (Spring Data JPA)
├── service/
│   ├── DocumentService.java       (Orquestra persistência)
│   └── ExtratorDecisaoService.java (Adicionado método extrairDeArquivoLocal)
├── controller/
│   ├── DocumentoController.java   (API REST com persistência)
│   └── ExtratorDecisaoController.java (Legado, sem persistência)
├── dto/
│   └── DocumentoListaDTO.java     (DTO para listagem)
└── config/
    └── DocumentStorageConfig.java (Config de armazenamento)
```

---

## Configurações em application.properties

```properties
# Database (H2 em memória)
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=update

# Armazenamento
document.storage.base-path=uploads/documentos

# OCR (mantém comportamento anterior)
ocr.tesseract.datapath=C:/workspace_eclipse
ocr.pdf.usar-texto-nativo=true
```

---

## Fluxo de Upload Etapa 2

```
POST /api/documentos (multipart/form-data)
    ↓
DocumentoController.uploadDocumento()
    ↓
DocumentService.processarUpload()
    ├─ Gera caminho único (UUID)
    ├─ Salva arquivo em uploads/documentos/UUID.pdf
    ├─ Cria registro Document no BD com status UPLOADING
    ├─ Chama processarOCR()
    │   ├─ Muda status para PROCESSING
    │   ├─ Chama ExtratorDecisaoService.extrairDeArquivoLocal()
    │   ├─ Extrai texto + metadados
    │   ├─ Muda status para COMPLETED/ERROR
    │   └─ Salva registro atualizado
    └─ Retorna DocumentoListaDTO com resumo
```

---

## Testes Recomendados

1. ✅ Upload PDF legalizado (com texto nativo)
2. ✅ Upload PDF scaneado (sem texto nativo, vai triggar OCR)
3. ✅ Upload imagem JPG/PNG
4. ✅ Listar documentos e verificar status
5. ✅ Buscar por critérios (processo, matéria, etc)
6. ✅ Verificar diretório uploads/ foi criado
7. ✅ Acessar H2 console e verificar tabela documento
8. ✅ Deletar documento via DELETE /api/documentos/{id}
9. ✅ Verificar arquivo foi removido do filesystem

---

## Próximos Passos (Etapa 3)

- Extrair metadados automáticos do texto (processo, matéria, datas)
- Implementar persistência assíncrona (TaskScheduler para OCR em background)
- Adicionar suporte a banco de dados persistente (PostgreSQL)
- Melhorar tratamento de erros e logging

