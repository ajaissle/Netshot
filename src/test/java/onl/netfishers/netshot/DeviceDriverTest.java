package onl.netfishers.netshot;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import onl.netfishers.netshot.device.Config;
import onl.netfishers.netshot.device.Device;
import onl.netfishers.netshot.device.DeviceDriver;
import onl.netfishers.netshot.device.Domain;
import onl.netfishers.netshot.device.Network4Address;
import onl.netfishers.netshot.device.NetworkAddress;
import onl.netfishers.netshot.device.Device.NetworkClass;
import onl.netfishers.netshot.device.DeviceDriver.DriverProtocol;
import onl.netfishers.netshot.device.access.Cli;
import onl.netfishers.netshot.device.access.Snmp;
import onl.netfishers.netshot.device.attribute.ConfigLongTextAttribute;
import onl.netfishers.netshot.device.attribute.ConfigTextAttribute;
import onl.netfishers.netshot.device.attribute.DeviceNumericAttribute;
import onl.netfishers.netshot.device.attribute.DeviceTextAttribute;
import onl.netfishers.netshot.device.credentials.DeviceCliAccount;
import onl.netfishers.netshot.device.credentials.DeviceCredentialSet;
import onl.netfishers.netshot.device.credentials.DeviceSshAccount;
import onl.netfishers.netshot.device.script.SnapshotCliScript;
import onl.netfishers.netshot.work.TaskLogger;

public class DeviceDriverTest {

	@BeforeAll
	static void initNetshot() throws Exception {
		Netshot.initConfig();
		DeviceDriver.refreshDrivers();
	}

	/**
	 * CLI class to mock a connection to a device.
	 */
	public static abstract class FakeCli extends Cli {

		protected PrintStream srvOutStream;
		protected DeviceCliAccount cliAccount;

		public FakeCli(NetworkAddress host, DeviceCliAccount cliAccount, TaskLogger taskLogger) throws IOException {
			super(host, taskLogger);
			PipedOutputStream pipedStream = new PipedOutputStream();
			this.srvOutStream = new PrintStream(pipedStream);
			this.inStream = new PipedInputStream(pipedStream, 16384);
			this.cliAccount = cliAccount;
		}

		@Override
		public void connect() throws IOException {
			// Nothing
		}

		@Override
		public void disconnect() {
			// Nothing
		}
	}

	/**
	 * Fake CiscoIOS12 CLI.
	 */
	public static class CiscoIOS12FakeCli extends FakeCli {
		public static enum CliMode {
			INIT,
			DISABLE,
			ENABLE,
			ENABLE_PASSWORD,
		}

		private CliMode cliMode = CliMode.INIT;
		private String hostname = "router1";
		private Pattern commandPattern = Pattern.compile("(.+)\r");

		public CiscoIOS12FakeCli(NetworkAddress host, DeviceCliAccount cliAccount, TaskLogger taskLogger) throws IOException {
			super(host, cliAccount, taskLogger);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case DISABLE:
					this.srvOutStream.printf("%s>", this.hostname);
					break;
				case ENABLE:
					this.srvOutStream.printf("%s#", this.hostname);
					break;
				default:
					break;
			}
		}

		protected void cmdShowVersion() {
			this.srvOutStream.print(
				"Cisco IOS XE Software, Version 03.16.07b.S - Extended Support Release\r\n" +
				"Cisco IOS Software, CSR1000V Software (X86_64_LINUX_IOSD-UNIVERSALK9-M), Version 15.5(3)S7b, RELEASE SOFTWARE (fc1)\r\n" +
				"Technical Support: http://www.cisco.com/techsupport\r\n" +
				"Copyright (c) 1986-2018 by Cisco Systems, Inc.\r\n" +
				"Compiled Fri 02-Mar-18 08:11 by mcpre\r\n" +
				"\r\n" +
				"\r\n" +
				"ROM: IOS-XE ROMMON\r\n" +
				"\r\n" +
				this.hostname + " uptime is 18 weeks, 1 day, 43 minutes\r\n" +
				"Uptime for this control processor is 18 weeks, 1 day, 45 minutes\r\n" +
				"System returned to ROM by reload at 18:12:05 UTC Sun Feb 21 2021\r\n" +
				"System image file is \"bootflash:packages.conf\"\r\n" +
				"Last reload reason: <NULL>\r\n" +
				"\r\n" +
				"License Level: ax\r\n" +
				"License Type: Default. No valid license found.\r\n" +
				"Next reload license Level: ax\r\n" +
				"\r\n" +
				"cisco CSR1000V (VXE) processor (revision VXE) with 1090048K/6147K bytes of memory.\r\n" +
				"Processor board ID 90PIQM03HLS\r\n" +
				"4 Gigabit Ethernet interfaces\r\n" +
				"32768K bytes of non-volatile configuration memory.\r\n" +
				"3022136K bytes of physical memory.\r\n" +
				"7774207K bytes of virtual hard disk at bootflash:.\r\n" +
				"\r\n" +
				"Configuration register is 0x2102\r\n"
			);
		}

		protected void cmdShowRunning() {
			this.srvOutStream.print(
				"!\r\n" +
				"! Last configuration change at 12:12:12 UTC Sat Jan 12 2022 by admin\r\n" +
				"!\r\n" +
				"version 15.5\r\n" +
				"service timestamps debug datetime msec\r\n" +
				"service timestamps log datetime msec\r\n" +
				"!\r\n" +
				"hostname router1\r\n" +
				"!\r\n" +
				"boot-start-marker\r\n" +
				"boot-end-marker\r\n" +
				"!\r\n" +
				"no aaa new-model\r\n" +
				"!\r\n" +
				"no ip domain lookup\r\n" +
				"!\r\n" +
				"interface Loopback0\r\n" +
				" ip address 10.255.0.1 255.255.255.255\r\n" +
				"!\r\n" +
				"interface GigabitEthernet1\r\n" +
				" description Management\r\n" +
				" ip address 192.168.200.101 255.255.255.0\r\n" +
				" negotiation auto\r\n" +
				"!\r\n" +
				"interface GigabitEthernet2\r\n" +
				" ip address 10.0.0.1 255.255.255.254\r\n" +
				" ip ospf network point-to-point\r\n" +
				" negotiation auto\r\n" +
				"!\r\n" +
				"interface GigabitEthernet3\r\n" +
				" no ip address\r\n" +
				" shutdown\r\n" +
				" negotiation auto\r\n" +
				"!\r\n" +
				"interface GigabitEthernet4\r\n" +
				" no ip address\r\n" +
				" shutdown\r\n" +
				" negotiation auto\r\n" +
				"!\r\n" +
				"router ospf 1\r\n" +
				" network 10.0.0.0 0.0.0.1 area 0\r\n" +
				" network 10.255.0.1 0.0.0.0 area 0\r\n" +
				"!\r\n" +
				"ip forward-protocol nd\r\n" +
				"!\r\n" +
				"no ip http server\r\n" +
				"no ip http secure-server\r\n" +
				"ip ssh version 2\r\n" +
				"!\r\n" +
				"access-list 98 permit 192.168.200.0 0.0.0.255\r\n" +
				"!\r\n" +
				"snmp-server community cisco RO 98\r\n" +
				"snmp-server location SNMPLOCATION\r\n" +
				"snmp-server contact SNMPCONTACT\r\n" +
				"snmp-server enable traps config\r\n" +
				"!\r\n" +
				"!\r\n" +
				"control-plane\r\n" +
				"!\r\n" +
				"!\r\n" +
				"line con 0\r\n" +
				" stopbits 1\r\n" +
				"line vty 0 4\r\n" +
				" login local\r\n" +
				" transport input telnet ssh\r\n" +
				"line vty 5 15\r\n" +
				" login local\r\n" +
				" transport input telnet ssh\r\n" +
				"!\r\n" +
				"ntp server pool.ntp.org\r\n" +
				"!\r\n" +
				"end\r\n"
			);
		}

		protected void cmdShowInventory() {
			this.srvOutStream.print(
				"NAME: \"Chassis\", DESCR: \"Cisco CSR1000V Chassis\"\r\n" +
				"PID: CSR1000V          , VID: V00, SN: 96NETS96HOT\r\n" +
				"\r\n" +
				"NAME: \"module R0\", DESCR: \"Cisco CSR1000V Route Processor\"\r\n" +
				"PID: CSR1000V          , VID: V00, SN: JAB1616161C\r\n" +
				"\r\n" +
				"NAME: \"module F0\", DESCR: \"Cisco CSR1000V Embedded Services Processor\"\r\n" +
				"PID: CSR1000V          , VID:    , SN:\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet1() {
			this.srvOutStream.print(
				"GigabitEthernet1 is up, line protocol is up\r\n" +
				"Hardware is CSR vNIC, address is 5000.0001.0000 (bia 5000.0001.0000)\r\n" +
				"Internet address is 192.168.200.101/24\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet2() {
			this.srvOutStream.print(
				"GigabitEthernet2 is up, line protocol is up\r\n" +
				"Hardware is CSR vNIC, address is 5000.0001.0001 (bia 5000.0001.0001)\r\n" +
				"Internet address is 10.0.0.1/31\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet3() {
			this.srvOutStream.print(
				"GigabitEthernet3 is administratively down, line protocol is down\r\n" +
				"Hardware is CSR vNIC, address is 5000.0001.0002 (bia 5000.0001.0002)\r\n"
			);
		}

		protected void cmdShowInterfaceGigabitEthernet4() {
			this.srvOutStream.print(
				"GigabitEthernet4 is administratively down, line protocol is down\r\n" +
				"Hardware is CSR vNIC, address is 5000.0001.0003 (bia 5000.0001.0003)\r\n"
			);
		}

		protected void cmdShowInterfaceLoopback0() {
			this.srvOutStream.print(
				"Loopback0 is up, line protocol is up\r\n" +
				"Internet address is 10.255.0.1/32\r\n"
			);
		}



		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.DISABLE;
				this.printPrompt();
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				// Echo command back
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.ENABLE) &&
						("show running-config".equals(command) || "show startup-config".equals(command))) {
					this.cmdShowRunning();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE) && "show inventory".equals(command)) {
					this.cmdShowInventory();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE)) &&
						"show version".equals(command)) {
					this.cmdShowVersion();
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE)) &&
						"terminal length 0".equals(command)) {
					this.printPrompt();
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE)) &&
						command.startsWith("show interface")) {
					if (command.startsWith("show interface GigabitEthernet1 ")) {
						this.cmdShowInterfaceGigabitEthernet1();
					}
					else if (command.startsWith("show interface GigabitEthernet2 ")) {
						this.cmdShowInterfaceGigabitEthernet2();
					}
					else if (command.startsWith("show interface GigabitEthernet3 ")) {
						this.cmdShowInterfaceGigabitEthernet3();
					}
					else if (command.startsWith("show interface GigabitEthernet4 ")) {
						this.cmdShowInterfaceGigabitEthernet4();
					}
					else if (command.startsWith("show interface Loopback0 ")) {
						this.cmdShowInterfaceLoopback0();
					}
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.DISABLE) && "enable".equals(command)) {
					this.cliMode = CliMode.ENABLE_PASSWORD;
					this.srvOutStream.printf("Password: ");
				}
				else if ((this.cliMode.equals(CliMode.ENABLE) || this.cliMode.equals(CliMode.DISABLE))) {
					this.srvOutStream.printf(
						"% Bad IP address or host name% Unknown command or computer name, or unable to find computer address\n");
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.ENABLE_PASSWORD)) {
					if (this.cliAccount.getSuperPassword().equals(command)) {
						this.cliMode = CliMode.ENABLE;
					}
					else {
						this.srvOutStream.print("% Bad secrets\n");
						this.cliMode = CliMode.DISABLE;
					}
					this.printPrompt();
				}
			}
		}
	}

	@Nested
	@DisplayName("CiscoIOS12 driver test")
	class CiscoIOS12Test {

		TaskLogger taskLogger = new FakeTaskLogger();
		Session fakeSession = new FakeSession();

		@Test
		@DisplayName("CiscoIOS12 Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
					IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new CiscoIOS12FakeCli(null, credentials, taskLogger);
			Session fakeSession = new FakeSession();
			Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
			SnapshotCliScript script = new SnapshotCliScript(true);
			Method runMethod = SnapshotCliScript.class.getDeclaredMethod("run", Session.class,
				Device.class, Cli.class, Snmp.class, DriverProtocol.class, DeviceCredentialSet.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, fakeSession, device, fakeCli, null, DriverProtocol.SSH, credentials);
			Assertions.assertEquals(device.getName(), "router1", "The device name is incorrect");
			Assertions.assertEquals(device.getSoftwareVersion(), "15.5(3)S7b", "The software version is incorrect");
			Assertions.assertEquals(device.getFamily(), "Cisco CSR1000V", "The device family is incorrect");
			Assertions.assertEquals(device.getLocation(), "SNMPLOCATION", "The location is incorrect");
			Assertions.assertEquals(device.getContact(), "SNMPCONTACT", "The contact is incorrect");
			Assertions.assertEquals(device.getNetworkClass(), NetworkClass.ROUTER, "The network class is incorrect");
			Assertions.assertEquals(
				((DeviceNumericAttribute)device.getAttribute("mainMemorySize")).getNumber(),
				1071.0, "The memory size is incorrect");
			Assertions.assertEquals(
				((DeviceTextAttribute)device.getAttribute("configRegister")).getText(),
				"0x2102", "The config register is incorrect");
			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals(config.getAuthor(), "admin", "The config author is incorrect");
			Assertions.assertEquals(
				((ConfigTextAttribute)config.getAttribute("iosImageFile")).getText(),
				"bootflash:packages.conf", "The IOS image file is incorrect");
			Assertions.assertTrue(((ConfigLongTextAttribute)config.getAttribute("runningConfig"))
				.getLongText().getText().contains("ip ssh version 2"), "The running config is not correct");
			Assertions.assertTrue(device.getModules().get(0).isRemoved(), "The first module is not set as removed");
			Assertions.assertEquals(device.getModules().get(2).getSerialNumber(),
				"96NETS96HOT", "The first module serial number is incorrect");
			Assertions.assertEquals(device.getNetworkInterface("GigabitEthernet1").getIp4Addresses().iterator().next(),
				Network4Address.getNetworkAddress("192.168.200.101", 24), "The first interface IP address is incorrect");
		}
	}

	/**
	 * Fake ZPE Node Grid CLI.
	 */
	public static class ZPENodeGridFakeCli extends FakeCli {
		public static enum CliMode {
			INIT,
			BASIC,
			SCREEN,
			PAGING,
		}

		private CliMode cliMode = CliMode.INIT;
		private Pattern commandPattern = Pattern.compile("(.*)\r");
		private String hostname = "NODEGRID-1";
		private String currentPath = "/";
		private List<String> pagedLines;

		public ZPENodeGridFakeCli(NetworkAddress host, DeviceCliAccount cliAccount, TaskLogger taskLogger) throws IOException {
			super(host, cliAccount, taskLogger);
		}

		private void printPrompt() {
			switch (this.cliMode) {
				case BASIC:
					this.srvOutStream.printf("[%s@%s %s]# ", this.cliAccount.getUsername(), this.hostname, this.currentPath);
					break;
				default:
					break;
			}
		}

		protected void printPaged(String text) {
			if (text != null) {
				this.pagedLines = new ArrayList<>(Arrays.asList(text.split("\n")));
			}
			int i = 0;
			StringBuffer output = new StringBuffer();
			Iterator<String> lineIt = this.pagedLines.iterator();
			while (lineIt.hasNext() && (i++ < 13)) {
				output.append(lineIt.next());
				output.append("\n");
				lineIt.remove();
			}
			this.srvOutStream.print(output);
			if (this.pagedLines.size() > 0) {
				this.cliMode = CliMode.PAGING;
				this.srvOutStream.print("-- more --:");
			}
			else {
				this.cliMode = CliMode.BASIC;
				this.printPrompt();
			}
		}

		protected void cmdShowSystemAbout() {
			this.printPaged(
				"system: NodeGrid Serial Console\r\n" +
				"licenses: 16\r\n" +
				"software: v3.1.16 (Jul 16 2016 - 16:16:16)\r\n" +
				"cpu: Intel(R) Atom(TM) CPU E3827  @ 1.74GHz\r\n" +
				"cpu_cores: 2\r\n" +
				"bogomips_per_core: 3416.16\r\n" +
				"serial_number: 1416161616\r\n" +
				"uptime: 16 days, 16 hours, 16 minutes\r\n" +
				"model: NSC-T16S\r\n" +
				"part_number:  NSC-T16S-STND-DAC-F-SFP\r\n" +
				"bios_version: 80168T00\r\n" +
				"psu: 2\r\n" +
				"\u0007" // Beep
			);
		}

		protected void cmdShowSystemUsageMemory() {
			this.printPaged(
				"  memory type  total (kb)  used (kb)  free (kb)\r\n" +
				"  ===========  ==========  =========  =========\r\n" +
				"  Mem          3934644     2224184    1710460  \r\n" +
				"  Swap         976892      520612     456280   \r\n" +
				"\u0007"
			);
		}

		protected void cmdShowSettingsDevices() {
			this.printPaged(
				"  * name                     connected through  type          access \r\n" +
				"  * =======================  =================  ============  =======\r\n" +
				"  monitoring   \r\n" +
				"  =============\r\n" +
				"  * AAA-AAA-AAA-AAA-AAAA    ttyS1              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * BBB-BBB-BBB-BBB-BBBB    ttyS2              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * CCC-CCC-CCC-CCC-CCCC    ttyS3              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * DDD-DDD-DDD-DDD-DDDD    ttyS4              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * EEE-EEE-EEE-EEE-EEEE    ttyS5              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * FFF-FFF-FFF-FFF-FFFF    ttyS6              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * GGG-GGG-GGG-GGG-GGGG    ttyS7              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * HHH-HHH-HHH-HHH-HHHH    ttyS8              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * III-III-III-III-IIII    ttyS9              local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * JJJ-JJJ-JJJ-JJJ-JJJJ    ttyS10             local_serial  enabled\r\n" +
				"  not supported\r\n" +
				"  * usbS1                   usbS1              usb_serialB   enabled\r\n" +
				"  not supported\r\n"
			);
		}

		protected void cmdShowSettings() {
			this.srvOutStream.print(
				"/settings/system_preferences help_url=http://www.zpesystems.com/ng/v3_0/NodeGrid-UserGuide-v3_0.pdf\r\n" +
				"/settings/system_preferences idle_timeout=1500\r\n" +
				"/settings/system_preferences enable_banner=no\r\n" +
				"/settings/network_connections/ETH0 ethernet_interface=eth0\r\n" +
				"/settings/network_connections/ETH0 connect_automatically=no\r\n" +
				"/settings/network_connections/ETH0 set_as_primary_connection=yes\r\n" +
				"/settings/network_connections/ETH0 enable_lldp=no\r\n" +
				"/settings/network_connections/ETH0 ipv4_mode=dhcp\r\n" +
				"/settings/network_connections/ETH0 ipv6_mode=address_auto_configuration\r\n" +
				"/settings/network_connections/ETH1 ethernet_interface=eth1\r\n" +
				"/settings/network_connections/ETH1 connect_automatically=no\r\n" +
				"/settings/network_connections/ETH1 set_as_primary_connection=no\r\n" +
				"/settings/network_connections/ETH1 enable_lldp=no\r\n" +
				"/settings/network_connections/ETH1 ipv4_mode=dhcp\r\n" +
				"/settings/network_connections/ETH1 ipv6_mode=address_auto_configuration\r\n" +
				"/settings/network_connections/bond connect_automatically=yes\r\n" +
				"/settings/network_connections/bond set_as_primary_connection=no\r\n" +
				"/settings/network_connections/bond enable_lldp=no\r\n" +
				"/settings/network_connections/bond primary_interface=eth0\r\n" +
				"/settings/network_connections/bond secondary_interface=eth1\r\n" +
				"/settings/network_connections/bond bonding_mode=active_backup\r\n" +
				"/settings/network_connections/bond link_monitoring=mii\r\n" +
				"/settings/network_connections/bond monitoring_frequency=100\r\n" +
				"/settings/network_connections/bond link_up_delay=0\r\n" +
				"/settings/network_connections/bond link_down_delay=0\r\n" +
				"/settings/network_connections/bond arp_validate=none\r\n" +
				"/settings/network_connections/bond bond_mac_policy=primary_interf\r\n" +
				"/settings/network_connections/bond ipv4_mode=static\r\n" +
				"/settings/network_connections/bond ipv4_address=10.10.16.16\r\n" +
				"/settings/network_connections/bond ipv4_bitmask=24\r\n" +
				"/settings/network_connections/bond ipv4_gateway=10.10.16.254\r\n" +
				"/settings/network_connections/bond ipv6_mode=no_ipv6_address\r\n" +
				"/settings/snmp/system syscontact=support@zpesystems.com\r\n" +
				"/settings/snmp/system syslocation=\"Nodegrid \"\r\n" +
				"/settings/local_accounts/admin username=admin\r\n" +
				"\r\n"
			);
		}

		protected void cmdEventSystemAudit() {
			this.srvOutStream.print(
				"<2022-01-03T04:01:16Z> Event ID 201: A user logged out of the system. User: alib/r/naba@10.16.2.3. Session type: HTTPS.\r\n" +
				"<2022-01-03T11:21:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n" +
				"<2022-01-03T11:21:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r\n" +
				"<2022-01-03T12:23:16Z> Event ID 200: A user logged into the system. User: homer@/r/n10.16.2.3. Session type: SSH. Authentication Method: TACACS+.\r\n" +
				"<2022-01-03T12:23:16Z> Event ID 201: A user logged out of the system. User: home/r/nr@10.16.2.3. Session type: SSH.\r\n" +
				"<2022-01-04T01:52:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n" +
				"<2022-01-04T01:53:16Z> Event ID 201: A user logged out of the system. User: nets/r/nhot@10.16.2.16. Session type: SSH.\r\n" +
				"<2022-01-04T02:52:16Z> Event ID 108: The configuration has changed. Change made by user: homer.\r\n" +
				"<2022-01-04T03:04:16Z> Event ID 200: A user logged into the system. User: netsho/r/nt@10.16.2.16. Session type: SSH. Authentication Method: TACACS+.\r\n"
			);
		}

		protected void cmdHostname() {
			this.printPaged(
				this.hostname + "\r\n"
			);
		}

		protected void write(String value) {
			if (this.cliMode == CliMode.INIT) {
				this.cliMode = CliMode.BASIC;
				this.printPrompt();
			}
			else if (this.cliMode == CliMode.SCREEN && "q".equals(value)) {
				this.cliMode = CliMode.BASIC;
				this.srvOutStream.print("\r\n");
				this.printPrompt();
				return;
			}
			Matcher commandMatcher = commandPattern.matcher(value);
			while (commandMatcher.find()) {
				String command = commandMatcher.group(1);
				this.srvOutStream.print(command + "\r\n");

				if (this.cliMode.equals(CliMode.PAGING) && "".equals(command)) {
					this.printPaged(null);
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *hostname")) {
					this.cmdHostname();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?system/about/?")) {
					this.cmdShowSystemAbout();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?system/system_usage/memory_usage/?")) {
					this.cmdShowSystemUsageMemory();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show /?settings/devices/?")) {
					this.cmdShowSettingsDevices();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *show_settings")) {
					this.cmdShowSettings();
					this.printPrompt();
				}
				else if (this.cliMode.equals(CliMode.BASIC) && command.matches("^ *event_system_audit")) {
					this.cmdEventSystemAudit();
					this.srvOutStream.print("(h->Help, q->Quit)");
					this.cliMode = CliMode.SCREEN;
				}
				else {
					this.srvOutStream.printf("Error: Invalid command: %s\r\n", command);
					this.printPrompt();
				}
			}
		}

	}

	@Nested
	@DisplayName("ZPENodeGrid driver test")
	class ZPENodeGridTest {

		TaskLogger taskLogger = new FakeTaskLogger();
		Session fakeSession = new FakeSession();

		@Test
		@DisplayName("ZPENodeGrid Snapshot")
		void snapshot() throws NoSuchMethodException, SecurityException, IOException,
					IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			DeviceCliAccount credentials = new DeviceSshAccount("admin", "admin", "admin", "admin/admin");
			Cli fakeCli = new ZPENodeGridFakeCli(null, credentials, taskLogger);
			Session fakeSession = new FakeSession();
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			Device device = new Device("ZPENodeGrid", null, domain, "test");
			SnapshotCliScript script = new SnapshotCliScript(true);
			Method runMethod = SnapshotCliScript.class.getDeclaredMethod("run", Session.class,
				Device.class, Cli.class, Snmp.class, DriverProtocol.class, DeviceCredentialSet.class);
			runMethod.setAccessible(true);
			runMethod.invoke(script, fakeSession, device, fakeCli, null, DriverProtocol.SSH, credentials);
			Assertions.assertEquals(device.getName(), "NODEGRID-1", "The device name is incorrect");
			Assertions.assertEquals(device.getSoftwareVersion(), "3.1.16", "The software version is incorrect");
			Assertions.assertEquals(device.getFamily(), "NSC-T16S", "The device family is incorrect");
			Assertions.assertEquals(device.getLocation(), "Nodegrid", "The location is incorrect");
			Assertions.assertEquals(device.getContact(), "support@zpesystems.com", "The contact is incorrect");
			Assertions.assertEquals(device.getNetworkClass(), NetworkClass.SWITCH, "The network class is incorrect");
			Assertions.assertEquals(
				((DeviceNumericAttribute)device.getAttribute("mainMemorySize")).getNumber(),
				3842.0, "The memory size is incorrect");
			Assertions.assertEquals(
				((DeviceNumericAttribute)device.getAttribute("licenseCount")).getNumber(),
				16, "The license count is incorrect");
			Assertions.assertEquals(
				((DeviceTextAttribute)device.getAttribute("cpuInfo")).getText(),
				"Intel(R) Atom(TM) CPU E3827  @ 1.74GHz (2 core(s))", "The CPU info is incorrect");
			Config config = device.getLastConfig();
			Assertions.assertNotNull(config, "The config doesn't exist");
			Assertions.assertEquals(config.getAuthor(), "homer", "The config author is incorrect");
			Assertions.assertEquals(
				((ConfigTextAttribute)config.getAttribute("softwareVersion")).getText(),
				"3.1.16", "The software version (in config) is incorrect");
			Assertions.assertTrue(((ConfigLongTextAttribute)config.getAttribute("settings"))
				.getLongText().getText().contains("enable_banner=no"), "The settings are not correct");
			Assertions.assertEquals(device.getModules().get(0).getSerialNumber(),
				"1416161616", "The first module serial number is incorrect");
			Assertions.assertEquals(device.getNetworkInterface("bond").getIp4Addresses().iterator().next(),
				Network4Address.getNetworkAddress("10.10.16.16", 24), "The bond interface IP address is incorrect");
			Assertions.assertNotNull(device.getNetworkInterface("usbS1"), "The usbS1 interface does not exist");
			Assertions.assertEquals(device.getNetworkInterface("ttyS10").getDescription(), "JJJ-JJJ-JJJ-JJJ-JJJJ",
				"The description of ttyS10 is incorrect");
		}
	}
	
}
