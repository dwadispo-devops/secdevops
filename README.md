## Créer un nouveau microservice

1. Copiez le dossier `_template/` et renommez la copie avec le nom du service 
   (ex: `medication-service`, `booking-service`).
2. Dans `pom.xml`, changez `<artifactId>service-template</artifactId>` par le nom du nouveau service.
3. Dans `application.properties`, remplacez `nom-du-service` et `nom_db` par les vrais noms 
   (ex: `medication-service`, `medication_db`), et changez le port (8082, 8083...).
4. Renommez le dossier `com/dwadispo/servicetemplate` en `com/dwadispo/votreservice`.
5. Ouvrez la classe principale et mettez à jour son nom de package et de classe.