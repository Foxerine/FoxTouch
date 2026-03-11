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

### Descarga

El APK de release ocupa solo **~9 MB**.

### Requisitos

- Android 11+ (API 30)
- Permiso de Servicio de Accesibilidad
- Permiso de superposición (mostrar sobre otras apps)
- Una clave API de al menos un proveedor LLM compatible

### Licencia

Todos los derechos reservados. Este proyecto aún no ha sido publicado bajo una licencia de código abierto.
