# SanChat 💬⚡

Chat com IA para Android (10+) usando a **NVIDIA NIM** através de um **proxy gratuito na Vercel** — a chave da API **nunca fica dentro do app**.

![SanChat](assets/banner.png)

## Recursos

- ⚡ **Streaming em tempo real** — resposta aparece palavra por palavra
- 🧠 **6 modelos** pra trocar na hora:
  - Llama 3.3 70B (geral)
  - Nemotron 70B (NVIDIA)
  - **DeepSeek R1** (raciocínio — com caixa de "pensamento" expansível!)
  - Gemma 2 27B (Google)
  - Qwen 2.5 Coder 32B (código)
  - Mistral Large 2
- 💬 **Multi-conversa** com histórico: criar, renomear, apagar
- 📝 Respostas em **markdown** (código, listas, links)
- 🌎 **Bilíngue**: português (padrão) ou inglês — troca nas Configurações
- 🎨 Tema **dark premium** com o verde NVIDIA (#76B900)
- 🔒 Arquitetura segura: app → proxy (Vercel) → NIM

## Instalar o APK

Baixe a versão mais nova em [Releases](../../releases) e instale (permita "fontes desconhecidas").

## Configurar o proxy (uma vez só, ~10 min, 100% grátis)

O app precisa de um proxy pra esconder sua chave da NVIDIA. Tudo no plano gratuito:

### 1. Chave da NVIDIA NIM (grátis)

1. Crie conta em [build.nvidia.com](https://build.nvidia.com)
2. Escolha um modelo e clique em **Get API Key**
3. Copie a chave (começa com `nvapi-`)

### 2. Deploy na Vercel (grátis)

1. Faça login em [vercel.com](https://vercel.com) com sua conta do GitHub
2. Clique em **Add New... → Project** e **importe este repositório**
3. Em **Framework Preset**, deixe **Other**
4. Abra **Environment Variables** e adicione:
   - `NVIDIA_API_KEY` = a sua chave `nvapi-...` (**obrigatório**)
   - `ACCESS_TOKEN` = qualquer senha secreta sua (opcional, recomendado — o app deve enviar o mesmo valor no campo "Token de acesso" das Configurações)
5. Clique em **Deploy**
6. Copie o domínio gerado (ex.: `https://sanchat-seuuser.vercel.app`)

### 3. Ligar o app no proxy

**Jeito fácil:** abra o SanChat → **Configurações** → cole a URL da Vercel → Salvar.

**Jeito automático (opcional):** no GitHub, adicione a **repository variable** `BACKEND_URL` com a URL da Vercel (Settings → Secrets and variables → Actions → Variables). As próximas builds já saem pré-configuradas.

## Estrutura

```
app/          App Android (Kotlin, minSdk 29)
api/chat.js   Proxy Edge Function (Vercel) — esconde a chave da NIM
.github/      Workflow que builda o APK assinado a cada push
```

## Segurança

- A `NVIDIA_API_KEY` vive **somente** nas env vars da Vercel
- O proxy tem **allowlist de modelos** e limite de tokens por resposta
- `ACCESS_TOKEN` (opcional) impede uso anônimo do seu proxy
- Custo: plano Hobby da Vercel é grátis (uso pessoal)

## Licença

MIT
