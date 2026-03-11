# FoxTouch

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [日本語](README_ja.md) | [한국어](README_ko.md) | **Español** | [Bahasa Melayu](README_ms.md)

FoxTouch es un agente de teléfono impulsado por IA para Android, inspirado en [Claude Code](https://docs.anthropic.com/en/docs/claude-code). Observa la pantalla a través de los Servicios de Accesibilidad, comprende los elementos de la interfaz y realiza acciones en nombre del usuario mediante instrucciones en lenguaje natural — llevando la experiencia de agente de codificación IA a la pantalla del teléfono.

> **Estado: Prueba de concepto / En desarrollo**
>
> Este proyecto se encuentra en una etapa experimental temprana. Las funciones pueden estar incompletas, ser inestables o cambiar significativamente.

### ⚠️ Avisos importantes

**Código generado por IA**: La gran mayoría del código de este proyecto fue generado por IA (Claude), con dirección y revisión humana. Esto incluye la arquitectura de la aplicación, la implementación de la interfaz, el diseño del sistema de herramientas, la integración de accesibilidad y los prompts del sistema.

**Uso bajo su propio riesgo**: Este software se proporciona "tal cual", sin garantía de ningún tipo. El agente de IA puede realizar acciones en tu teléfono, incluyendo toques, escritura, deslizamientos y lanzamiento de aplicaciones. **Eres el único responsable de cualquier consecuencia del uso de este software.** Los autores no asumen responsabilidad por daños, pérdida de datos, compras no intencionadas u otros problemas que puedan surgir de su uso.

### Cómo funciona

1. El usuario describe una tarea en lenguaje natural
2. FoxTouch lee la pantalla usando la API de Accesibilidad de Android
3. Un LLM (configurable) analiza la interfaz y decide las acciones
4. FoxTouch ejecuta acciones (tocar, escribir, desplazar, deslizar, etc.) a través de los Servicios de Accesibilidad
5. El ciclo observar-pensar-actuar se repite hasta completar la tarea

### Casos de uso

**Automatización diaria**

> **Tú:** "Pide comida a domicilio, unos $200 pesos, elige según mis gustos que te dije ayer, y usa el mejor cupón"
>
> FoxTouch recuerda tus preferencias desde la memoria → abre la app de delivery → navega restaurantes → planifica un pedido según tu presupuesto y gustos → aplica el mejor cupón → pide tu confirmación antes de pagar

> **Tú:** "Manda un WhatsApp a mamá diciendo que llego tarde, y pon una alarma a las 7am"
>
> FoxTouch abre WhatsApp → encuentra a mamá → escribe y envía el mensaje → abre Reloj → crea la alarma → confirma ambas tareas

**Información e investigación**

> **Tú:** "Compara el precio de AirPods Pro en Amazon y MercadoLibre, dime cuál es más barato"
>
> FoxTouch abre Amazon → busca y anota el precio → abre MercadoLibre → busca y anota el precio → reporta la comparación

> **Tú:** "Traduce todo lo que hay en esta pantalla al inglés"
>
> FoxTouch lee el contenido actual de la pantalla → proporciona la traducción completa en el chat

**Redes sociales**

> **Tú:** "Publica mi última foto en Instagram con un caption sobre el atardecer"
>
> FoxTouch abre Instagram → crea una nueva publicación → selecciona la última foto → escribe el caption → pide confirmación antes de publicar

**Check-in diario y notificaciones**

> **Tú:** "Haz mi check-in diario en Genshin Impact"
>
> FoxTouch abre el juego → navega a la página de check-in → reclama la recompensa diaria → guarda como habilidad reutilizable

> **Tú:** "Revisa todas mis notificaciones no leídas y dime si hay algo urgente"
>
> FoxTouch abre el panel de notificaciones → lee cada notificación → filtra y resume las importantes → descarta el resto

### Funciones principales

- **Soporte multi-proveedor LLM** — OpenAI, Anthropic Claude, Google Gemini, OpenRouter y cualquier API compatible con OpenAI
- **Comprensión de pantalla** — Análisis del árbol de elementos UI + capturas anotadas con cuadrícula de coordenadas y etiquetas
- **Interacción completa con el dispositivo** — tocar, escribir, desplazar, deslizar, pulsación larga, pellizcar, atrás/inicio, lanzar apps
- **Modo plan** — las tareas complejas se planifican y revisan antes de ejecutarse
- **Sistema de habilidades** — guardar y reutilizar planes de acción
- **Seguimiento de tareas** — progreso en múltiples pasos visible en tiempo real
- **Superposición flotante** — controla el agente desde cualquier aplicación
- **IME integrado** — método de entrada invisible para entrada de texto y acceso al portapapeles en todas las apps
- **Captura mejorada** — respaldo MediaProjection para apps que bloquean capturas de accesibilidad
- **Compactación de contexto** — resumen automático de conversaciones para mantenerse dentro de los límites de tokens
- **Entrada de voz y TTS** — conversación por voz + lectura de respuestas en voz alta
- **Memoria persistente** — el agente recuerda instrucciones y contexto entre sesiones
- **Protecciones de seguridad** — aprobación por herramienta, permisos por nivel de riesgo, modo YOLO
- **Interfaz multilingüe** — English, 简体中文, 繁體中文, 日本語, 한국어, Español, Bahasa Melayu

### Diseño en detalle

<details>
<summary><b>Auto-compactación de contexto</b> — Resumen automático de conversación al acercarse al límite de tokens</summary>

Cuando una tarea larga llena la ventana de contexto del LLM, FoxTouch compacta automáticamente la conversación en lugar de fallar o perder contexto.

**Umbrales escalonados** — Ventanas más pequeñas se compactan antes (70% para ≤128K); ventanas más grandes pueden llenarse más (85% para >500K). Base de datos interna de 100+ modelos con descubrimiento API en tiempo de ejecución.

**Resumen inteligente** — El prompt de compactación exige preservar detalles accionables: mensajes del usuario literales, IDs de elementos, coordenadas, nombres de paquetes, rutas de error y el "siguiente paso" concreto.

**Continuación sin interrupciones** — Tras resumir, limpia el historial, reinyecta el prompt del sistema con contexto actualizado del dispositivo, y añade el resumen como mensaje del usuario con instrucciones de "continuar como si la interrupción nunca hubiera ocurrido".

**Modelo de compactación separado** — Puede usar un modelo más económico/rápido para resumir (ej: Haiku para resumir, Opus para ejecutar).

</details>

<details>
<summary><b>Sistema de anotación de capturas</b> — Cuatro capas visuales independientes para razonamiento espacial del LLM</summary>

Al leer la pantalla, el agente puede solicitar hasta cuatro capas de anotación:

1. **Cuadrícula de coordenadas** — Líneas cada 200px como referencia espacial
2. **Límites de elementos** — Rectángulos coloreados: verde=clicable, azul=desplazable, naranja=editable, gris=otro. Cada uno con `[ID]`
3. **Etiquetas de texto/clase** — Nombre de clase y contenido de texto del elemento
4. **Marcadores de clic** — Verificación post-acción mostrando exactamente dónde cayó el toque

Todas las coordenadas usan el espacio de pantalla original — la conversión es interna.

</details>

<details>
<summary><b>Abstracción multi-proveedor LLM</b> — Una interfaz, tres APIs muy diferentes</summary>

Todos los proveedores emiten un `Flow<LLMEvent>` unificado, pero manejan internamente diferencias significativas:

- **Claude** — Streaming SSE nativo de Anthropic. Normalización de roles de mensajes y fusión de mensajes consecutivos del mismo rol. Soporte de pensamiento extendido.

- **Gemini** — Fuerza modo no-streaming con herramientas (el `thoughtSignature` es inestable en streaming). La firma debe devolverse en mensajes posteriores.

- **OpenAI** — Transforma propiedades opcionales a tipos nullable y hace todas las propiedades obligatorias, forzando al modelo a enviar `null` explícito para parámetros no usados.

</details>

<details>
<summary><b>Entrada de texto en 3 niveles</b> — Fallback en cascada para compatibilidad universal</summary>

1. **IME integrado** (FoxTouchIME) — InputMethodService invisible. Más fiable para WebView, Flutter y controles personalizados. **Exento de restricciones de lectura del portapapeles de Android 10+**.

2. **ACTION_SET_TEXT** — Acción de accesibilidad estándar en el nodo enfocado.

3. **Pegado desde portapapeles** — Último recurso: escribe en el portapapeles y envía `ACTION_PASTE`.

Cambio automático de IME: activar → enviar texto → restaurar teclado anterior, ~1.5 segundos.

</details>

<details>
<summary><b>Sistema de seguridad y permisos</b> — Niveles de riesgo graduales con cambio de modo en tiempo de ejecución</summary>

Cada herramienta declara un nivel de riesgo (bajo/medio/alto). El usuario puede responder a solicitudes de aprobación con: Permitir, Permitir siempre, Permitir todo (modo YOLO solo para el turno actual) o Denegar. `confirm_completion` garantiza que el agente siempre pregunte antes de completar.

</details>

<details>
<summary><b>Modo plan</b> — Fase de observación restringida antes de la ejecución</summary>

En tareas complejas, el agente entra en modo plan donde solo puede usar herramientas de lectura y planificación. Crea un plan estructurado para revisión del usuario; solo tras la aprobación se desbloquean las herramientas de interacción. Los planes pueden guardarse como habilidades reutilizables.

</details>

<details>
<summary><b>Arquitectura de superposición</b> — Jetpack Compose renderizado en servicio de superposición del sistema</summary>

El panel flotante es un `ComposeView` + `TYPE_APPLICATION_OVERLAY`, renderizado con un `LifecycleOwner` personalizado. Cambia dinámicamente entre modos enfocable/no-enfocable según el estado del agente. Antes de cada captura, oculta las superposiciones y espera 50ms para un frame limpio.

</details>

<details>
<summary><b>Acceso al portapapeles</b> — Bypass en 3 etapas de las restricciones de Android</summary>

Contra las restricciones de lectura de portapapeles de Android 10+: ruta IME (exento de restricciones) → contexto del servicio de accesibilidad → Activity transparente (obtiene foco de ventana temporalmente, completado en 2 segundos).

</details>

### Descarga

El APK de release ocupa solo **~9 MB**.

### Requisitos

- Android 11+ (API 30)
- Permiso de Servicio de Accesibilidad
- Permiso de superposición (mostrar sobre otras apps)
- Una clave API de al menos un proveedor LLM compatible

### Licencia

Todos los derechos reservados. Este proyecto aún no ha sido publicado bajo una licencia de código abierto.
