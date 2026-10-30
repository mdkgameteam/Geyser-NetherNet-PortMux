# Geyser-NetherNet-PortMux (MDKPortMux)

[![Status: Beta](https://img.shields.io/badge/Status-Beta%20(Boredom%20Project%20xd)-yellow.svg)](#)
[![Minecraft Bedrock](https://img.shields.io/badge/Minecraft%20Bedrock-1.21.60%2B%20(26.60)-green.svg)](https://geysermc.org)
[![Minecraft Java](https://img.shields.io/badge/Minecraft%20Java-Paper%20%2F%20Purpur%20%2F%20Spigot-blue.svg)](https://papermc.io)
[![Protocol](https://img.shields.io/badge/NetherNet-Built--in%20Signaling-orange.svg)](https://github.com/GeyserMC/Geyser)
[![Dual Stack](https://img.shields.io/badge/Network-IPv4%20%26%20IPv6-purple.svg)](#)
[![Donate: PayPal](https://img.shields.io/badge/Donate-PayPal-00457C.svg?logo=paypal&logoColor=white)](https://www.paypal.com/donate/?business=bendyydavid300%40outlook.com&no_recurring=0&item_name=Support+Geyser-NetherNet-PortMux&currency_code=USD)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> ⚠️ **Nota / Warning:** Este plugin está actualmente en **fase BETA**. Lo creamos en un rato libre porque estábamos aburridos y queríamos jugar con nuestros amigos usando el mismo puerto sin tener que bancarnos el rollo de NetherNet y las caídas de Warden xd. ¡Funciona de 10, pero úsalo bajo tu propio criterio y cualquier feedback o issue es bienvenido!
> 
> *(This plugin is currently in **BETA**. We coded it because we were bored and just wanted to play with friends using the same port without dealing with NetherNet / Warden issues xd. Works great, feedback and PRs are welcome!)*

> **MDKPortMux** es un plugin ligero para **Paper / Purpur / Spigot** que permite utilizar **el mismo puerto** para Minecraft Java y Minecraft Bedrock con el nuevo protocolo **NetherNet (1.21.60+)**, de forma **100% autónoma, sin Warden ni intermediarios externos** y con soporte completo **Dual-Stack (IPv4 / IPv6)**.

---

## ⚡ ¿Qué problema resuelve?

A partir de **Minecraft Bedrock 1.21.60 (v26.60)**, Mojang eliminó el soporte de **RakNet**. Ahora todos los clientes Bedrock modernos utilizan **NetherNet**.

NetherNet requiere un servidor de señalización HTTP sobre TCP para el apretón de manos inicial (WebRTC):
* Si intentas usar el mismo puerto de tu servidor Java (ej: `25565` o el puerto de tu host), Geyser y Paper colisionan en el socket TCP.
* La solución por defecto de Geyser es recurrir a **Warden (`*.wdn.gg`)**, un proxy externo de terceros que introduce latencia, caídas y límites de conexión, o forzar a los jugadores a conectarse a puertos separados para Java y Bedrock.

### La Solución: `MDKPortMux`
**MDKPortMux** se inyecta en el pipeline asíncrono de red (**Netty**) del servidor Paper:
1. Inspecciona los primeros bytes entrantes en la conexión TCP compartida.
2. Si detecta tráfico HTTP (`GET` / `POST` de Bedrock NetherNet), le inyecta la cabecera **HAProxy PROXY Protocol v1** (para conservar la IP real del jugador) y lo desvía internamente a Geyser (puerto `19132`).
3. Si detecta una conexión de Minecraft Java, la entrega directamente al pipeline nativo de Paper.
4. El tráfico de juego WebRTC corre en paralelo por UDP en ese mismo puerto sin interferencias.

---

## 🚀 Características

- ✅ **Mismo Puerto para Ambos:** Tanto jugadores de Java como de Bedrock se conectan a `tudominio.com:PUERTO`.
- ✅ **Cero Dependencias de Terceros:** No requiere Warden ni servidores externos (`signaling.mode: builtin`).
- ✅ **Soporte Dual-Stack:** Compatible con IPv4 nativo/túnel e IPv6 directo.
- ✅ **Preservación de IP Real:** Pasa la IP remota del cliente a Geyser mediante HAProxy v1.
- ✅ **Cero Lag:** Solo interviene durante el handshake inicial (menos de 1 segundo). El juego en vivo corre en UDP WebRTC nativo.

---

## 📦 Instalación y Configuración

### 1. Descargar e Instalar
Descarga el archivo [`MDKPortMux-1.0.0.jar`](releases/MDKPortMux-1.0.0.jar) y colócalo en tu carpeta `plugins/`:
* `plugins/MDKPortMux-1.0.0.jar`
* `plugins/Geyser-Spigot.jar`
* `plugins/floodgate-spigot.jar`

### 2. Configurar Geyser (`plugins/Geyser-Spigot/config.yml`)
Abre tu `config.yml` de Geyser y ajusta las siguientes secciones:

```yaml
bedrock:
  # "::" para Dual-Stack (IPv4 e IPv6) o "0.0.0.0" si solo usas IPv4
  address: "::"
  # Puerto interno donde Geyser escucha la señalización (MDKPortMux reenvía aquí)
  port: 19132
  # Coloca aquí el MISMO puerto de tu servidor Java (ej: 25565 o el de tu host)
  webrtc-port: 25565
  transport: nethernet
  clone-remote-port: false

signaling:
  # 100% alojado en tu servidor, sin Warden
  mode: builtin
  # Tu dominio/IP pública y el puerto donde entran los jugadores
  advertise-addresses:
    - "tudominio.com:25565"

advanced:
  java:
    # IMPORTANTE: debe ser false para la conexión interna directa a Paper
    use-haproxy-protocol: false
  bedrock:
    # IMPORTANTE: en true para recibir la IP real que MDKPortMux le entrega
    use-haproxy-protocol: true
    haproxy-protocol-whitelisted-ips: ["127.0.0.1", "::1"]
```

### 3. Iniciar el Servidor
Inicia tu servidor Paper. En la consola verás:
```text
[MDKPortMux] Multiplexor inyectado con exito en 2 canal(es) de Paper!
[MDKPortMux] Trafico HTTP (Bedrock NetherNet) -> 127.0.0.1:19132
[MDKPortMux] Trafico Minecraft Java -> Pipeline nativo de Paper
[Geyser-Spigot] Built-in signaling started on http://:::19132, with NetherNet on UDP port 25565
```

¡Y listo! Los jugadores de Java y Bedrock ya pueden conectarse al mismo puerto.

---

## 🛠️ Compilación desde el Código Fuente

Requisitos: Java 21+ (o Java 25 si usas Paper 26.3+).

```bash
# Compilar las clases
javac -cp "paper.jar:plugins/*" -d bin src/main/java/xyz/mdkgameteam/portmux/*.java
cp src/main/resources/plugin.yml bin/

# Crear el JAR
jar -cvf MDKPortMux-1.0.0.jar -C bin .
```

---

## 💖 Apoyar el proyecto / Support

Si este plugin te sirvió, te ahorró dolores de cabeza con NetherNet/Warden y te permitió jugar con tus amigos en el mismo puerto, ¡puedes apoyarnos con una donación para un café!

Cualquier aporte ayuda a seguir manteniendo el plugin, optimizándolo y actualizándolo para futuras versiones de Minecraft y Geyser.

[![Donate with PayPal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/donate/?business=bendyydavid300%40outlook.com&no_recurring=0&item_name=Support+Geyser-NetherNet-PortMux&currency_code=USD)

* **Enlace directo de PayPal:** [Hacer una donación vía PayPal](https://www.paypal.com/donate/?business=bendyydavid300%40outlook.com&no_recurring=0&item_name=Support+Geyser-NetherNet-PortMux&currency_code=USD)

---

## 📄 Licencia

Este proyecto está bajo la licencia [MIT](LICENSE). Libre para usar, modificar y distribuir.
Creado por **MDK Team** (mdkgameteam@gmail.com).
