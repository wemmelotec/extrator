(function () {
    var form = null;
    var status = null;
    var resultadosBody = null;
    var recarregarBtn = null;
    var ultimaUrlConsulta = null;

    function formatDate(value) {
        if (!value) {
            return "-";
        }

        var date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString("pt-BR");
    }

    function setStatus(message, isError) {
        status.textContent = message || "";
        status.style.color = isError ? "#972d2d" : "#114b5f";
    }

    function renderResultados(documentos) {
        resultadosBody.innerHTML = "";

        if (!documentos || documentos.length === 0) {
            resultadosBody.innerHTML = '<tr><td colspan="6" class="empty-row">Nenhum documento encontrado.</td></tr>';
            return;
        }

        documentos.forEach(function (doc) {
            var tr = document.createElement("tr");
            var statusClass = (doc.status || "").toUpperCase();

            tr.innerHTML =
                "<td>" + (doc.id || "-") + "</td>" +
                "<td>" + (doc.nomeOriginal || "-") + "</td>" +
                "<td><span class=\"status-pill " + statusClass + "\">" + (doc.status || "-") + "</span></td>" +
                "<td>" + (doc.numeroProcesso || "-") + "</td>" +
                "<td>" + (doc.materia || "-") + "</td>" +
                "<td>" + formatDate(doc.dataUpload) + "</td>";

            resultadosBody.appendChild(tr);
        });
    }

    function executarConsulta(url) {
        ultimaUrlConsulta = url;
        setStatus("Consultando backend...", false);

        fetch(url)
            .then(function (response) {
                if (!response.ok) {
                    return response.text().then(function (message) {
                        throw new Error(message || "Falha na consulta.");
                    });
                }
                return response.json();
            })
            .then(function (data) {
                renderResultados(data);
                setStatus("Consulta realizada com sucesso.", false);
            })
            .catch(function (error) {
                resultadosBody.innerHTML = '<tr><td colspan="6" class="empty-row">' + error.message + "</td></tr>";
                setStatus(error.message, true);
            });
    }

    function onSubmit(event) {
        event.preventDefault();
        executarConsulta("/api/documentos");
    }

    function onRecarregar() {
        if (ultimaUrlConsulta) {
            executarConsulta(ultimaUrlConsulta);
            return;
        }

        executarConsulta("/api/documentos");
    }

    function bootstrap() {
        form = document.getElementById("consultaForm");
        status = document.getElementById("consultaStatus");
        resultadosBody = document.getElementById("consultaResultadosBody");
        recarregarBtn = document.getElementById("recarregarConsulta");

        if (!form || !status || !resultadosBody || !recarregarBtn) {
            return;
        }

        form.addEventListener("submit", onSubmit);
        recarregarBtn.addEventListener("click", onRecarregar);
    }

    document.addEventListener("DOMContentLoaded", bootstrap);
})();
