import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <section class="hero">
      <h1>Trouvez vos médicaments sans courir de pharmacie en pharmacie</h1>
      <p>
        Photographiez votre ordonnance, vérifiez la liste lue par l'IA, puis réservez
        en pharmacie et retirez avec un code.
      </p>
      <a class="btn" routerLink="/scan">Scanner mon ordonnance</a>
      <a class="btn" routerLink="/pharmacien/demandes">Espace pharmacien : demandes reçues</a>
    </section>

    <section class="cards">
      <article class="card">
        <h2>1. Scanner</h2>
        <p>Prenez en photo ou importez votre ordonnance.</p>
      </article>
      <article class="card">
        <h2>2. Vérifier</h2>
        <p>Confirmez la liste des médicaments reconnus.</p>
      </article>
      <article class="card">
        <h2>3. Réserver</h2>
        <p>Choisissez une pharmacie proche et retirez avec votre code.</p>
      </article>
    </section>
  `,
})
export class HomePage {}
