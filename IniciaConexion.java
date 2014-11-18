// Un servidor debe tener gente que atienda personalmente a cada cliente.
// Cuando un cliente necesita hacer algo, lo solicita al servidor.
// Â¿QuÃ© servicios se tienen disponibles?
/*
 Verificar que una boleta entrante pueda ser atendida.
 Actualizar un registro.
 Proporsionar informaciÃ³n de progreso.
 */

/*
 El Â¿quÃ© desea hacer?
 Puede ser que quiera comprobar la conexiÃ³n, comprobar un usuario o
 llevar a cabo alguna acciÃ³n.
 */

/*
 El servidor sabe quiÃ©n tiene la base de datos.
 */

/*
 El paquete de datos ayuda a quien atiende, a saber quÃ© quiere el cliente.
 */
package servidor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.ListIterator;

public class IniciaConexion extends Thread {

	private LinkedList<String> capturistas;
	private LinkedList<String> participantes;
	private String databaseHost;
	private String[] opcionesVoto;
	/* A ver cosas feas del mal, ando haciendo pruebas jajaja no se crean no son del mal*/
	public IniciaConexion(String databaseHost, String[] opcionesVoto) {
		this.databaseHost = databaseHost;
		this.opcionesVoto = opcionesVoto;
		capturistas = new LinkedList<String>();
		participantes = new LinkedList<String>();
	}

	class Auxiliar extends Thread {
		ObjectOutputStream salidaObjeto;
		ObjectInputStream entradaObjeto;
		Socket socket;
		ConexionBD conexionBD;
		String[] datos;
		String[] databaseConnectionData;

		public Auxiliar(Socket socket) {
			this.socket = socket;
			databaseConnectionData = new String[4];
		}

		@Override
		public void run() {
			try {
				entradaObjeto = new ObjectInputStream(socket.getInputStream());
				datos = (String[]) entradaObjeto.readObject();
				System.out.println("Un usuario ha sido aceptado. " + datos[0]
						+ ", " + datos[1] + ", " + datos[2]);
				if (datos[0].equals("")) {
					entradaObjeto.close();
					socket.close();
					return;
				}
				if (datos[2].equals("")) {
					databaseConnectionData[0] = databaseHost;
					databaseConnectionData[1] = "Votacion";
					databaseConnectionData[2] = datos[0];
					databaseConnectionData[3] = datos[1];
					conexionBD = new ConexionBD(databaseConnectionData);
					Connection c = conexionBD.getConnection();
					salidaObjeto = new ObjectOutputStream(
							socket.getOutputStream());
					salidaObjeto.flush();
					if (c == null) {
						System.out.println("Usuario incorrecto");
						salidaObjeto.writeUTF("NO");
						salidaObjeto.flush();
					} else {
						if (datos[0].equals("Capturista")) {
							salidaObjeto.writeUTF("YES");
							salidaObjeto.flush();
							String capturistaHost = socket.getInetAddress()
									.getHostAddress();
							synchronized (capturistas) {
								if (!capturistas.contains(capturistaHost)) {
									capturistas.add(capturistaHost);
								}
							}
						} else {
							if (datos[0].equals("Participante")) {
								synchronized (capturistas) {
									salidaObjeto.writeObject(capturistas);
									salidaObjeto.flush();
								}
							} else {
								if (datos[0].equals("Consultor")) {
									System.out
											.println("Sí se intenta mandar esto");
									salidaObjeto.writeUTF("YES");
									salidaObjeto.flush();
								} else {
									if (datos[0].equals("sa")) {
										salidaObjeto.writeUTF("YES");
										salidaObjeto.flush();
									}
								}
							}
						}
					}
					entradaObjeto.close();
					salidaObjeto.close();
					socket.close();
					return;
				}

				if (datos[0].equals("Capturista")) {
					salidaObjeto = new ObjectOutputStream(
							socket.getOutputStream());
					if (datos.length != 4) {
						synchronized (participantes) {
							ListIterator<String> iter = participantes
									.listIterator();
							while (iter.hasNext()) {
								if (iter.next().contains(
										socket.getInetAddress()
												.getHostAddress())) {
									String[] pair = iter.previous()
											.split(" - ");
									salidaObjeto.writeUTF(pair[1]);
									salidaObjeto.close();
									entradaObjeto.close();
									socket.close();
									return;
								}
							}
						}
						salidaObjeto
								.writeUTF("Aún no hay una terminal emparejada.");
						salidaObjeto.close();
						entradaObjeto.close();
						socket.close();
						return;
					}
					databaseConnectionData[0] = databaseHost;
					databaseConnectionData[1] = "Votacion";
					databaseConnectionData[2] = datos[0];
					databaseConnectionData[3] = datos[1];
					conexionBD = new ConexionBD(databaseConnectionData);
					Connection c = conexionBD.getConnection();
					try {
						c.createStatement().execute(
								"insertaBoleta '" + datos[2] + "';");
						salidaObjeto
								.writeUTF("Boleta registrada correctamente.");
						salidaObjeto.close();
						entradaObjeto.close();
						socket.close();
						Socket slaveTerminal = new Socket(datos[3], 36523);
						ObjectOutputStream salida = new ObjectOutputStream(
								slaveTerminal.getOutputStream());
						LinkedList<String> dataOptionsList = new LinkedList<String>();
						for (String iter : opcionesVoto) {
							dataOptionsList.add(iter);
						}
						dataOptionsList.addFirst(datos[2]);
						salida.writeObject(dataOptionsList);
						salida.close();
						slaveTerminal.close();
					} catch (SQLException e) {
						if (e.toString().contains("resultados.")) {
							System.out
									.println("El metodo contains() sí hace eso");
						} else {
							if (e.toString().contains("violation")) {
								salidaObjeto.writeUTF("La Boleta ya existe.");
								salidaObjeto.close();
								entradaObjeto.close();
								socket.close();
							} else {
								salidaObjeto.writeUTF(e.toString());
								salidaObjeto.close();
								entradaObjeto.close();
								socket.close();
							}
						}
					}
					return;
				}
				/*
				 * Si el participante manda sólo tres datos, quiere decir que
				 * está seleccionando Capturista.
				 */
				if (datos[0].equals("Participante")) {
					salidaObjeto = new ObjectOutputStream(
							socket.getOutputStream());
					if (datos.length != 4) {
						String participanteHost = socket.getInetAddress()
								.getHostAddress();
						try {
							synchronized (capturistas) {
								if (capturistas.contains(datos[2])) {
									Socket capturista = new Socket(datos[2],
											33401);
									ObjectOutputStream salida = new ObjectOutputStream(
											capturista.getOutputStream());
									salida.writeUTF(participanteHost);
									salida.flush();
									salida.close();
									capturista.close();
									capturistas.remove(datos[2]);
									salidaObjeto.writeUTF("Registrado.");
									salidaObjeto.flush();
								} else {
									salidaObjeto
											.writeUTF("Te ganaron el capturista.");
									salidaObjeto.flush();
								}
							}
						} catch (IOException e) { // Deja al participante en
													// lista de espera.
							String pair = datos[2] + " - "
									+ socket.getInetAddress().getHostAddress();
							synchronized (participantes) {
								if (!participantes.contains(pair)) {
									participantes.add(pair);
								}
							}
						}
						salidaObjeto.close();
						entradaObjeto.close();
						socket.close();
						return;
					}
					databaseConnectionData[0] = databaseHost;
					databaseConnectionData[1] = "Votacion";
					databaseConnectionData[2] = datos[0];
					databaseConnectionData[3] = datos[1];
					conexionBD = new ConexionBD(databaseConnectionData);
					Connection c = conexionBD.getConnection();
					try {
						c.createStatement().execute(
								"insertarVoto '" + datos[2] + "','" + datos[3]
										+ "';");
						salidaObjeto.writeUTF("Success");
					} catch (SQLException e) {
						System.out.println(e);
						salidaObjeto.writeUTF(e.toString());
						salidaObjeto.flush();
					}
					salidaObjeto.close();
					entradaObjeto.close();
					socket.close();
					return;
				}
				if (datos[0].equals("Consultor")) {
					databaseConnectionData[0] = databaseHost;
					databaseConnectionData[1] = "Votacion";
					databaseConnectionData[2] = datos[0];
					databaseConnectionData[3] = datos[1];
					salidaObjeto = new ObjectOutputStream(
							socket.getOutputStream());
					conexionBD = new ConexionBD(databaseConnectionData);
					Connection c = conexionBD.getConnection();
					try {
						LinkedList<String> Resultados = new LinkedList<String>();
						Statement stmnt = c.createStatement();
						ResultSet rs;
						for (String iter : opcionesVoto) {
							rs = stmnt.executeQuery("consultarVotos '" + iter
									+ "';");
							String aux = new String();
							while (rs.next()) {
								aux = aux.concat(iter + "@" + rs.getString(1));
							}
							Resultados.add(aux);
							System.out.println(aux);
						}
						salidaObjeto.writeObject(Resultados);
						salidaObjeto.flush();
					} catch (SQLException e) {
						System.out.println(e);
						salidaObjeto.writeObject(e);
						salidaObjeto.flush();
					}
					salidaObjeto.close();
					entradaObjeto.close();
					socket.close();
					return;
				}
				if (datos[0].equals("sa")) {
					ObjectOutputStream salidaObjeto = new ObjectOutputStream(
							socket.getOutputStream());
					String temp = new String();
					String[] results;
					databaseConnectionData[0] = databaseHost;
					databaseConnectionData[1] = "Votacion";
					databaseConnectionData[2] = datos[0];
					databaseConnectionData[3] = datos[1];
					conexionBD = new ConexionBD(databaseConnectionData);
					Connection c = conexionBD.getConnection();
					Statement stmnt;
					ResultSet rs;
					if (c != null) {
						try {
							System.out.println("Great Kayosama");
							stmnt = c.createStatement();
							rs = stmnt.executeQuery(datos[2]);
							while (rs.next()) {
								temp = temp + rs.getString(1) + "_"
										+ rs.getString(2) + "@";
							}
							results = temp.split("@");
							salidaObjeto.writeObject(results);
							salidaObjeto.flush();
							for (String iter : results) {
								System.out.println(iter);
							}
						} catch (SQLException sqlEx) {
							System.out.println("Error de sql: " + sqlEx);
							String[] respuesta = { "Error SQL: "
									+ sqlEx.toString() };
							salidaObjeto.writeObject(respuesta);
							salidaObjeto.flush();
						}
					}
				}
			} catch (IOException e) {
				System.out.println(e);
			} catch (ClassNotFoundException e) {
			}
		}
	}

	@Override
	public void run() {
		ServerSocket servidor = null;
		try {
			servidor = new ServerSocket(23543);
			while (true) {
				Socket socket = servidor.accept();
				Auxiliar auxiliar = new Auxiliar(socket);
				auxiliar.start();
			}
		} catch (IOException e) {
			try {
				servidor.close();
			} catch (IOException ex) {
			}
		}
	}
}
