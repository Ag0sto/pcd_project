# 🎮 IsKahoot - Projeto PCD

## 📌 Descrição

O **IsKahoot** é uma aplicação distribuída inspirada no Kahoot, desenvolvida no âmbito da unidade curricular de **Programação Concorrente e Distribuída (PCD)**.

O sistema permite que múltiplos jogadores participem em quizzes em tempo real, respondendo a perguntas e acumulando pontuações, com suporte a concorrência e comunicação cliente-servidor.

---

## 👨‍💻 Autores

* Rafael Costa
* Tiago Eliseu

---

## ⚙️ Funcionalidades

* 🧠 Carregamento de perguntas a partir de ficheiros JSON
* 🖥️ Interface gráfica para o cliente
* 🌐 Comunicação cliente-servidor (sockets)
* 🧵 Suporte a múltiplos jogadores (multithreading)
* ⏱️ Sistema de tempo para respostas
* 🏆 Sistema de pontuação por jogador/equipa

---

## 🏗️ Arquitetura

O projeto está organizado em vários módulos:

```
src/
 ├── client/        # Interface e lógica do cliente
 ├── server/        # Servidor e gestão do jogo
 ├── model/         # Estruturas de dados (Question, Player, etc.)
 ├── network/       # Comunicação entre cliente e servidor
 └── concurrency/   # Sincronização e controlo de threads
```

---

## 📂 Tecnologias utilizadas

* ☕ Java
* 📦 Maven
* 🔗 Sockets TCP
* 🧵 Multithreading
* 📄 Gson (JSON parsing)

---

## ▶️ Como executar

### 1. Compilar o projeto

```bash
mvn clean install
```

### 2. Executar o servidor

```bash
mvn exec:java -Dexec.mainClass="pt.projetopcd.iskahoot.server.Server"
```

### 3. Executar o cliente

```bash
mvn exec:java -Dexec.mainClass="pt.projetopcd.iskahoot.client.Client"
```

---

## 📄 Formato das perguntas

As perguntas são carregadas de um ficheiro JSON:

```json
{
  "name": "Quiz",
  "questions": [
    {
      "question": "Pergunta?",
      "correct": 1,
      "points": 5,
      "options": ["A", "B", "C", "D"]
    }
  ]
}
```

---

## 🚧 Estado do Projeto

🟡 Em desenvolvimento

* [x] Leitura de perguntas (JSON)
* [x] Estrutura base cliente-servidor
* [ ] Sistema completo de jogo
* [ ] Sincronização avançada
* [ ] Ranking final

---

## 💡 Objetivos

* Aplicar conceitos de concorrência
* Implementar comunicação distribuída
* Desenvolver uma aplicação interativa em tempo real

---

## 📜 Licença

Projeto desenvolvido para fins académicos.

