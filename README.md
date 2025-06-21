# Administrador de VPN
## Resumo

**Administrador de VPN** se trata de um programa que cria, importa e deleta certificados de VPN conforme solicitação de um usuário autenticado e autorizado. Também permite
- Adminstrar usuários (no caso de um usuário administrador)
- Criar e revogar certificados VPN através da integração com scripts em Bash
- Realizar Download do arquivo ZIP da VPN do usuário

## Estrutura do projeto
![image](https://github.com/user-attachments/assets/d3e788de-1ff9-4a1f-b20e-48cc02c4ae6b)


```
_________________________________________
Diagrama das divisões de Rede 
├── 192.168.202.0 <Rede intranet>
│   └── 192.168.202.1 <OpenVpnServer> 
│   └── 192.168.202.3 <Rede intranet>
├── 192.168.200.0 <Rede de serviços>
│   └── 192.168.200.1 <FireWall> 
│   └── 192.168.200.2 <OpenVpnServer>
│   └── 192.168.200.10 <DB MySQL>
├── 192.XXX.XXX.0 <LAN física>
│   └── 192.XXX.XXX.1 <Gateway/Moden usuário> 
    └── 192.XXX.XXX.10 <Cliente VPN201 (Máquina do usuário)>
_________________________________________
```
## Ferramentas Necessárias
- Java 17+ (Com Spring Boot 3.x)
- Maven
- Scripts em Bash
- MySQL 8
- Certificados SSL

## Construção de ambiente
### Máquina virtual OpenVPNServer
#### Network Interfaces
- Inicialmente realizar a atualização da vm usando o comando `sudo apt update -y`
Para a criação da OpenVPNServer, é necessário realizar a configuração de rede no arquivo `/etc/network/interfaces` com os seguintes dados
```
-- sudo nano /etc/network/interfaces

[Configuração do arquivo]
______________________________________________________________________
# this file describes the network interfaces available on your system
# and hoe to activate them. For more information, see interfaces(5).

source /etc/network/interfaces.d/*

# the loopback network interface
auto lo
iface lo inet loopback

# the primary network interface
allow-hotplug enp0s3
iface enp0s3 inet static
    address 192.168.200.2
    network 192.168.200.0
    netmask 255.255.255.0
    broadcast 192.168.200.255
    gateway 192.168.200.1

allow-hotplug enp0s8
iface enp0s8 inet static
    address 192.168.202.1
    network 192.168.202.0
    netmask 255.255.255.0
    broadcast 192.168.200.255
______________________________________________________________________
```
> É necessário realizar o reboot da vm usando `sudo reboot` e atualizar os dados com `sudo apt update -y` depois da edição do arquivo `/etc/network/interfaces` para cada uma das VMs citadas
#### Scripts para criar e revogar Certificados
Seguir para a pasta `/home/usuario` e adicionar o script `createCert.sh` 
```bash
-- cd /home/usuario
-- sudo nano createCert.sh

[Script de criação de certificado]
______________________________________________________________________
#!/bin/bash

if [ "$EUID" -ne 0 ]
then
        echo "Por favor, execute como root"
        exit 1
fi

export EASYRSA_PASSIN="pass:123456"

EASYRSA_DIR="/usr/share/easy-rsa"
OUTPUT_DIR="/home/exports"
VPN_BASE_DIR="/etc/openvpn/client"

mkdir -p "$VPN_BASE_DIR"
mkdir -p "$OUTPUT_DIR"

CLIENTE="$1"
if [ -z "$CLIENTE" ];then
        echo "Parâmetro vazio, insira o nome do cliente como parâmetro"
        unset EASYRSA_PASSIN
        exit 1
fi

#Geração da chave
cd "$EASYRSA_DIR" || exit
echo "" | ./easyrsa gen-req "$CLIENTE" nopass

#Assinatura do certificado
echo "yes" | ./easyrsa sign-req client "$CLIENTE"

CLIENT_DIR="$VPN_BASE_DIR/$CLIENTE"
mkdir -p "$CLIENT_DIR"

cp pki/ca.crt "$CLIENT_DIR/"
cp "pki/issued/$CLIENTE.crt" "$CLIENT_DIR/"
cp "pki/private/$CLIENTE.key" "$CLIENT_DIR/"
cp pki/dh.pem "$CLIENT_DIR/" 2>/dev/null || echo "Arquivo dh.pem não encontrado"


cat <<EOF> "$CLIENT_DIR/$CLIENTE.ovpn"
client
dev tun
proto udp
remote <ip-da-placa-externa-do-firewall> 1194

ca ca.crt
cert $CLIENTE.crt
key $CLIENTE.key

tls-client
resolv-retry infinite
nobind
persist-key
persist-tun
EOF

OUTPUT_CLIENT_DIR="$OUTPUT_DIR/$CLIENTE"
cp -r "$CLIENT_DIR" "$OUTPUT_CLIENT_DIR"

chown -R tomcat:tomcat "$OUTPUT_CLIENT_DIR"

cd "$OUTPUT_DIR" || exit
zip -r "${CLIENTE}.zip" "$CLIENTE"

chmod 744 "$OUTPUT_DIR/$CLIENTE.zip"
rm -rf "$OUTPUT_CLIENT_DIR"

echo "VPN para $CLIENTE gerada em $OUTPUT_DIR/${CLIENTE}.zip"

unset EASYRSA_PASSIN

#Finalizar com Ctrl+X, Enter e Ctrl+o
______________________________________________________________________
```
Para o script revoga os certificados criar o arquivo `revokeCert.sh`

```bash
-- sudo nano revokeCert.sh
______________________________________________________________________
#!/bin/bash

if [ "$EUID" -ne 0 ]; then
    echo "Por favor, execute como root"
    exit 1
fi

EASYRSA_DIR="/usr/share/easy-rsa"
VPN_BASE_DIR="/etc/openvpn/client"
OUTPUT_DIR="/home/exports"

CLIENTE="$1"
if [ -z "$CLIENTE" ]; then
    echo "Parâmetro vazio, insira o nome do cliente como parâmetro"
    exit 1
fi

export EASYRSA_PASSIN="pass:123456"

cd "$EASYRSA_DIR" || exit 1

# Revoga o certificado
echo "yes" | ./easyrsa revoke "$CLIENTE"
./easyrsa gen-crl

# Remove diretório de arquivos do cliente
rm -rf "$VPN_BASE_DIR/$CLIENTE"
rm -f "$OUTPUT_DIR/${CLIENTE}.zip"

# Atualiza CRL no OpenVPN (se aplicável)
cp "$EASYRSA_DIR/pki/crl.pem" /etc/openvpn/crl.pem
chmod 644 /etc/openvpn/crl.pem

unset EASYRSA_PASSIN

echo "Certificado e arquivos de $CLIENTE foram revogados e removidos."
______________________________________________________________________
```
#### Tomcat
Para realizar a instalação e configuração do Tomcat na máquina OpenVPNServer, seguir os seguintes comandos:
- `sudo apt update -y`
- `sudo apt install default-jdk -y`
> 1. criar usuario tomcat
- `sudo groupadd tomcat` 
- `sudo useradd -s /bin/false -g tomcat -d /opt/tomcat tomcat` 

> 2. instalar tomcat
- `sudo apt install curl -y`
- `cd /tmp/` 
- `curl -O https://dlcdn.apache.org/tomcat/tomcat-10/v10.1.42/bin/apache-tomcat-10.1.42.tar.gz`
- `sudo mkdir /opt/tomcat`
- `sudo tar xzvf apache-tomcat-10.1.42.tar.gz -C /opt/tomcat --strip-components=1`

> 3. criar grupo tomcat
- `sudo chgrp -R tomcat /opt/tomcat`
- `sudo chmod -R g+r /opt/tomcat/conf`
- `sudo chmod g+x /opt/tomcat/conf`
- `sudo chown -R tomcat /opt/tomcat/webapps/ /opt/tomcat/work/ /opt/tomcat/temp/  /opt/tomcat/logs/`

> 4. criando serviço tomcat
```
-- sudo nano /etc/systemd/system/tomcat.service

[Configuração do arquivo]

[Unit] 
Description=Apache Tomcat Web Application Container 
After=network.target 

[Service] 
Type=forking 
Environment=JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64 
Environment=CATALINA_PID=/opt/tomcat/temp/tomcat.pid 
Environment=CATALINA_HOME=/opt/tomcat 
Environment=CATALINA_BASE=/opt/tomcat 
Environment='CATALINA_OPTS=-Xms512M -Xmx1024M -server -XX:+UseParallelGC' 
Environment='JAVA_OPTS=-Djava.awt.headless=true - Djava.security.egd=file:/dev/./urandom' 

ExecStart=/opt/tomcat/bin/startup.sh 
ExecStop=/opt/tomcat/bin/shutdown.sh 

User=tomcat 
Group=tomcat 
UMask=0007 
RestartSec=60 
Restart=always 
[Install] 
WantedBy=multi-user.target 
```
> 5. ligando o serviço tomcat
- `sudo systemctl daemon-reload`
- `sudo systemctl enable tomcat`
- `sudo systemctl start tomcat`
- `sudo systemctl status tomcat `
  
> 6. configurando usuario admin interno do tomcat
- `sudo nano /opt/tomcat/conf/tomcat-users.xml`

Alterar a seguinte linha:

![image](https://github.com/user-attachments/assets/02e3719d-e3cb-4762-b5de-5c486cd162e8)

> 7. configurando o acesso admin para o painel manager-gui

- `sudo nano /opt/tomcat/webapps/manager/META-INF/context.xml`

Alterar a seguinte linha:

![image](https://github.com/user-attachments/assets/73e439dd-1c4d-4a1a-b798-b8642dd3cb21)

> 8. configurando acesso admin para o painel host-manager
- `sudo nano /opt/tomcat/webapps/host-manager/META-INF/context.xml`

Alterar a seguinte linha:

![image](https://github.com/user-attachments/assets/df949ba5-6c5b-4047-b99c-889e8c0983ac)


> 9. Para finalizar, salve ambos arquivos e reinicie o serviço tomcat
- `sudo systemctl restart tomcat.service` 
- `sudo systemctl status tomcat.service`

É necessário dar permissão sudo (sem senha) para o usuario tomcat executar os scripts da VPN

- `sudo visudo`

Alterar a seguinte linha:

![image](https://github.com/user-attachments/assets/84ee38ef-b249-492d-9c7c-5c51c7ba96eb)


### Firewall
Para configurar o Firewall é necessário configurar o arquivo `/etc/network/interfaces` conforme mostrado a seguir:
```
-- sudo nano /etc/network/interfaces

[Configurar o arquivo]
______________________________________________________________________
# this file describes the network interfaces available on your system
# and hoe to activate them. For more information, see interfaces(5).

source /etc/network/interfaces.d/*

# the loopback network interface
auto lo
iface lo inet loopback

# the primary network interface
allow-hotplug enp0s3
iface enp0s3 inet dhcp

# ip externo do firewall configurado como Bridge no Virtualbox
auto enp0s8
iface enp0s8 inet static
    address 192.168.200.1
    network 192.168.200.0
    netmask 255.255.255.0
    broadcast 192.168.201.255
______________________________________________________________________

```

- Alterar o arquivo `/etc/sysctl.conf ` retirando o comentário da linha `net.ipv4.ip_forward=1`
- Realizar Reboot do sistema `sudo reboot`
- Configurar as seguintes regras no arquivo `/usr/local/sbin/firewall.sh`
```bash
#!/bin/bash
#

nft flush ruleset
nft add table inet filter
nft add chain inet filter input '{ type filter hook input priority 0; }'
nft add rule inet filter input ip protocol tcp ct state established accept
nft add rule inet filter input ip protocol udp ct state established accept
nft add chain inet filter forward '{ type filter hook forward priority 0; }'
nft add rule inet filter forward ip protocol tcp ct state established accept
nft add rule inet filter forward ip protocol udp ct state established accept

nft add table inet nat
nft add chain inet nat prerouting '{ type nat hook prerouting priority -100 ; }'
nft add chain inet nat postrouting '{ type nat hook postrouting priority 100 ; }'
nft add rule inet nat postrouting oif enp0s3 masquerade

nft add rule inet nat prerouting iifname enp0s3 tcp dport 80 dnat ip to 192.168.200.2:8080
nft add rule inet filter forward iifname enp0s3 oifname enp0s8 ip daddr 192.168.200.2 tcp dport 8080 accept
```
Realizar os seguintes comandos:
- `sudo chmod +x /usr/local/sbin/firewall.sh`
- `sudo /usr/local/sbin/firewall.sh`
- `sudo nft list ruleset`
- Criar o arquivo firewall.service no caminho `/etc/systemd/system/firewall.service` e configurar da seguinte forma:
```
[Unit]
Description=Firewall para o projeto Administrador de VPN
After=network.target

[Service]
ExecStart=/usr/local/sbin/firewall.sh

[Install]
WantedBy=multi-user.target
```
Finalmente, realizar os seguintes comandos:
- `sudo systemctl enable firewall.service`
- `sudo systemctl reboot` 

#### Regras Iptables

Para criar as regras de direcionamento, seguir os seguintes comandos:
- `sudo apt install iptables-persistent -y`

Realizar os seguintes comandos:

> 1. portas do MySql:
- `iptables -t nat -A PREROUTING -d <ip_externo_do_firewall_bridged> -p tcp -m tcp --dport 3306 -j DNAT --to-destination 192.168.200.10:3306`
- `iptables -t nat -A POSTROUTING -d 192.168.200.10 -p tcp -m tcp --dport 3306 -j MASQUERADE`

> 2. portas do Tomcat (redirecionamento para 443)
- `iptables -t nat -A PREROUTING -d <ip_externo_do_firewall_bridged> -p tcp -m tcp --dport 443 -j DNAT --to-destination 192.168.200.2:80`
- `iptables -t nat -A PREROUTING -d <ip_externo_do_firewall_bridged> -p tcp -m tcp --dport 443 -j DNAT --to-destination 192.168.200.2:8080`

> 3. portas da OpenVPN
- `iptables -t nat -A PREROUTING -d <ip_externo_do_firewall_bridged> -p udp -m udp --dport 1194 -j DNAT --to-destination 192.168.200.2:1194`

> 4. portas do MySQL

- `iptables -t nat -A PREROUTING -d <ip_externo_do_firewall_bridged> -p tcp -m tcp --dport 3306 -j DNAT --to-destination 192.168.200.10:3306`
- `iptables -t nat -A POSTROUTING -d 192.168.200.10 -p tcp -m tcp --dport 3306 -j MASQUERADE`

Ao final do dos comandos executar:
- `sudo netfilter-persistent save`
- `sudo netfilter-persistent reload`

### Servidor Banco de dados MySQL
Configurar o arquivo `/etc/network/interfaces`:
```
-- sudo nano /etc/network/interfaces

[Configurar o arquivo]
______________________________________________________________________
# this file describes the network interfaces available on your system
# and hoe to activate them. For more information, see interfaces(5).

source /etc/network/interfaces.d/*

# the loopback network interface
auto lo
iface lo inet loopback

# the primary network interface
allow-hotplug enp0s3
iface enp0s3 inet static
    address 192.168.200.10
    network 192.168.200.0
    netmask 255.255.255.0
    broadcast 192.168.200.255
    gateway 192.168.200.1
```
Em seguida realizar a instalação do Mysql com os seguintes comandos:
- `sudo apt update -y`
- `sudo apt install wget -y`
- `cd /tmp`
- `wget https://repo.mysql.com/mysql-apt-config_0.8.29-1_all.deb`
- `sudo apt install ./mysql-apt-config_0.8.29-1_all.deb`
- `sudo apt update -y`
- `sudo apt install mysql-server -y`
- `sudo systemctl enable --now mysql`
> Para entender se a ativação do mysql funcionou, use o comando `sudo systemctl status mysql`. O serviço precisa estar "enable"
>
> Para instalação seguir com a confirmação de todas as etapas.
![image](https://github.com/user-attachments/assets/64ac81cf-1758-4577-b484-1c8c7fc018a5)
> A imagem acima vai aparecer após a execução do comando `sudo apt install ./mysql-apt-config_0.8.29-1_all.deb`
![image](https://github.com/user-attachments/assets/75e3c540-b550-40ed-9e88-b293fa6b00da)
> A imagem acima vai aparecer após a execução do comando `sudo apt install mysql-server -y`

Para o uso do Mysql utilize o comando `sudo mysql -u root -p` e faça login com a senha `root@admin`


É necessário criar um usuário para a aplicação com todos os privilégios no banco de dados
- `CREATE DATABASE shadow_vpn;`
- `CREATE USER 'app_user'@'%' IDENTIFIED BY 'admin@webapp';`
- `GRANT ALL PRIVILEGES ON shadow_vpn.* TO 'app_user'@'%';`
- `FLUSH PRIVILEGES;`

### Estrutura do projeto web
#### Descrição

Um projeto web, feito usando o string boot e hibernate. O projeto visa a apresentação de carômetros de maneira simples. 
É feita a distinção de administrador e um aluno qualquer, cabendo ao aluno o preenchimento do formulário que será enviado com status pendente para o administrador.
Este pode aprovar ou reprovar os carômetros conforme o necessário. Apenas carômetros aprovados poderão ser expostos para todos os alunos.

* Java Development Kit (JDK).
* (Optional) IDE, such as VSCode, NetBeans or Eclipse.

#### Estrutura de pastas
````
C:.
├───.idea
├───.mvn
├───src
│   ├───main
│   │   ├───java
│   │   │   └───br
│   │   │       └───edu
│   │   │           └───fatec
│   │   │               └───shadowvpn
│   │   │                   ├───controller
│   │   │                   ├───entity
│   │   │                   ├───repository
│   │   │                   ├───security
│   │   │                   ├───service
│   │   │                   └───util
│   │   └───resources
│   │       ├───static
│   │       │   └───css
│   │       └───templates
│   │           ├───user
│   │           └───certificate
│   └───test
│       └───java
│           └───br
│               ├───com
│               └───edu
│                   └───fatec
│                       └───shadowvpn
└───target
    ├───classes
    ├───generated-sources
    │   └───annotations
    ├───generated-test-sources
    │   └───test-annotations
    ├───maven-archiver
    ├───maven-status
    ├───shadow-vpn-demo
    │   ├───META-INF
    │   └───WEB-INF
    │       ├───classes
    │       └───lib
    └───shadow-vpn-demo.war
````
#### Dependências do Pom
* É necessário que o pom apresente as seguintes dependências:
```
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-tomcat</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.thymeleaf.extras</groupId>
        <artifactId>thymeleaf-extras-springsecurity6</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```
#### Application.properties
* Localizado em `src/main/resources/application.properties`:
```properties 
# ====== # Application Configuration # ====== #
spring.application.name=shadow-vpn

# ====== # Database Configuration # ====== #
spring.datasource.url=jdbc:mysql://<ip_da_placa_bridged_do_firewall>:3306/shadow_vpn
spring.datasource.username=app_user
spring.datasource.password=admin@webapp
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ====== # JPA / Hibernate Configuration # ====== #
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# ====== # Spring Security Login # ====== #
spring.security.user.name=admin
spring.security.user.password=12345678

# ====== # Thymeleaf Configuration # ====== #
spring.thymeleaf.cache=false
```
#### Executando o Projeto!!!
* Para executar o projeto, é necessário importa-lo numa IDE (Preferêncialmente Eclipse) e Executar a aplicação no `ApiApplication.java`
* Detalhe importante: Importante ter Ambiente Maven atualizado e preparado, com lombok instalando.
* Importante criar o banco de dados mySQL antes de Iniciar o Projeto, e trocar a senha no application.properties `src/main/resources/application.properties`.
* Realizar a exportação do projeto como um arquivo de extensão `.war` e importar no Tomcat.
> Para acessar o Tomcat, utilizar o ip da vpn no buscador do google  logar com `admin` e senha `123456` 
* Após a primeira execução da aplicação web é necessário criar um usuário administrador direto no banco de dados para poder acessar a VPN Web, mantendo normas de segurança onde apenas o administrador pode cadastrar novos usuários 

- `sudo mysql -u root -p;`
- `Use database shadow_vpn;`
- Utilize a senha root cadastrada
- `INSERT INTO User (id, username, email, firtName, lastName, password, role) VALUES (1, 'admin', 'admin@mail', 'SeuNome', 'SeuSobreNome', '$2a$10$50lmyTFJVzGGUdlG7QQix.3g4zmotQhvE08qa/DWOwZEUOMBU7Mae', 'ADMIN');`
> Após isso, retornar a tela principal e logar com usuário `admin` e senha `root@123` que está criptografada acima

## Autores
Os responsáveis pelo projeto são:
* Gabriel Cavalcante Delfino
* Pedro Henrique Barros Silva
* Rafael Bezerra dos Santos
## Versão do projeto
* Versão: 1.0
    * Versão inicial 
