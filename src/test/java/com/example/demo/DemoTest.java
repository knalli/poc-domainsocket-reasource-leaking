package com.example.demo;

import com.github.dockerjava.transport.DomainSocket;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

class DemoTest {

	@Test
	void runWithExistingSocket() throws IOException {
		exec("/var/run/docker.sock");
	}

	@Test
	void runWithNotExistingSocket() throws IOException {
		exec("doesnotexist");
	}

	private static void exec(final String path) throws IOException {
		var openFilesCount = new CurrentOpenFiles();
		DomainSocket socket = null;

		try {
			System.out.printf("BEGIN = Current open files: %s%n", openFilesCount.get());
			System.out.println("Creating socket...");
			socket = DomainSocket.get(path);
		} catch (IOException e) {
			System.out.printf("Error: %s%n", e.getMessage());
		}

		System.out.printf("INIT = Current open files: %s%n", openFilesCount.get());

		if (socket != null) {
			System.out.println("Closing socket..");
			socket.close();
		} else {
			System.out.println("No open socket");
		}

		System.out.printf("EXIT = Current open files: %s%n", openFilesCount.get());
	}

	// extracted from FileDescriptorMetrics
	static class CurrentOpenFiles implements Supplier<Integer> {

		private static final List<String> UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES = Arrays.asList(
				"com.sun.management.UnixOperatingSystemMXBean", // HotSpot
				"com.ibm.lang.management.UnixOperatingSystemMXBean" // J9
		                                                                                        );

		private final OperatingSystemMXBean osBean;

		@Nullable
		private final Class<?> osBeanClass;

		@Nullable
		private final Method openFilesMethod;

		public CurrentOpenFiles() {
			this.osBean = ManagementFactory.getOperatingSystemMXBean();
			this.osBeanClass = getFirstClassFound(UNIX_OPERATING_SYSTEM_BEAN_CLASS_NAMES);
			this.openFilesMethod = detectMethod("getOpenFileDescriptorCount");
		}

		@Override
		public Integer get() {
			return Double.valueOf(invoke(openFilesMethod)).intValue();
		}

		private double invoke(@Nullable Method method) {
			try {
				return method != null ? (double) (long) method.invoke(osBean) : Double.NaN;
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				return Double.NaN;
			}
		}

		@Nullable
		private Method detectMethod(String name) {
			if (osBeanClass == null) {
				return null;
			}
			try {
				// ensure the Bean we have is actually an instance of the interface
				osBeanClass.cast(osBean);
				return osBeanClass.getDeclaredMethod(name);
			} catch (ClassCastException | NoSuchMethodException | SecurityException e) {
				return null;
			}
		}

		@Nullable
		private Class<?> getFirstClassFound(List<String> classNames) {
			for (String className : classNames) {
				try {
					return Class.forName(className);
				} catch (ClassNotFoundException ignore) {
				}
			}
			return null;
		}

	}

}
