import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <main>
      <section class="maison-hero">
        <div class="maison-hero__texte">
          <p class="maison-etiquette">Le soin, sans détour</p>

          <h1>
            DwaDispo,
            <em>simplifier</em>
            le lien entre patients et pharmacies
          </h1>

          <span class="maison-filet" aria-hidden="true"></span>

          <p class="maison-intro">
            Photographiez votre ordonnance, vérifiez les médicaments
            reconnus par l'IA, puis trouvez une pharmacie et réservez
            vos médicaments simplement.
          </p>

          <div class="maison-actions">
            <a
              class="maison-bouton maison-bouton--or"
              [routerLink]="connected() ? '/scan' : '/connexion'"
            >
              Scanner mon ordonnance
            </a>

            <a
              class="maison-bouton maison-bouton--contour"
              [routerLink]="connected() ? '/garde' : '/connexion'"
            >
              Pharmacies de garde
            </a>
          </div>
        </div>

        <div class="maison-hero__image">
          <img
            src="hero-medicaments.svg"
            width="1800"
            height="640"
            alt="Illustration autour de l'univers pharmaceutique"
          />
        </div>
      </section>

      <section class="maison-etapes" aria-label="Comment ça marche">
        <article>
          <span class="maison-numero">01</span>
          <h2>Scanner</h2>
          <p>Prenez en photo ou importez votre ordonnance.</p>
        </article>

        <article>
          <span class="maison-numero">02</span>
          <h2>Vérifier</h2>
          <p>Confirmez la liste des médicaments reconnus par l'IA.</p>
        </article>

        <article>
          <span class="maison-numero">03</span>
          <h2>Réserver</h2>
          <p>Choisissez une pharmacie proche et retirez vos médicaments.</p>
        </article>
      </section>

      <section class="maison-pro">
        <p class="maison-etiquette maison-etiquette--claire">
          Professionnels de santé
        </p>

        <h2>Vous êtes pharmacien ?</h2>

        <p>
          Rejoignez DwaDispo pour faciliter la gestion et le suivi
          des demandes de disponibilité.
        </p>

        <div class="maison-actions">
          <a
            class="maison-bouton maison-bouton--sombre"
            routerLink="/pharmacien/inscription"
          >
            Inscrire ma pharmacie
          </a>

          <a
            class="maison-bouton maison-bouton--contour-sombre"
            [routerLink]="connected() ? '/pharmacien/demandes' : '/pharmacien/connexion'"
          >
            Demandes reçues
          </a>
        </div>
      </section>

      <section class="maison-about">
        <p class="maison-etiquette">À propos de DwaDispo</p>
        <h2>Une approche numérique au service de la santé</h2>
        <p>
          Notre objectif est de proposer une expérience plus simple et
          plus accessible autour de l'information pharmaceutique, en
          mettant la technologie au service des patients et des professionnels
          de santé.
        </p>
      </section>
    </main>
  `,
  styles: [`
    .maison-hero {
      background: #161516;
      color: #f4eee0;
      text-align: center;
      padding: 4.5rem 1.5rem 0;
    }

    .maison-hero__texte {
      max-width: 820px;
      margin: 0 auto;
    }

    .maison-etiquette {
      margin: 0 0 1.2rem;
      font-size: .8rem;
      letter-spacing: .28em;
      text-transform: uppercase;
      color: #b89b5e;
    }

    .maison-hero h1 {
      margin: 0;
      font-size: clamp(2rem, 5vw, 3.7rem);
      line-height: 1.15;
      color: #fff;
    }

    .maison-hero h1 em {
      font-style: italic;
      color: #d8be82;
    }

    .maison-filet {
      display: block;
      width: 72px;
      height: 2px;
      margin: 1.8rem auto;
      background: var(--couleur-or);
    }

    .maison-intro {
      margin: 0 auto;
      max-width: 650px;
      font-size: 1.05rem;
      line-height: 1.75;
      color: #d9d2c3;
    }

    .maison-actions {
      display: flex;
      justify-content: center;
      align-items: center;
      flex-wrap: wrap;
      gap: 1rem;
      margin-top: 2rem;
    }

    .maison-bouton {
      display: inline-flex;
      justify-content: center;
      align-items: center;
      min-height: 48px;
      padding: .85rem 1.4rem;
      border: 1px solid transparent;
      border-radius: 4px;
      font-weight: 600;
      text-decoration: none;
      transition: transform .2s ease, background .2s ease;
    }

    .maison-bouton:hover {
      transform: translateY(-2px);
    }

    .maison-bouton--or {
      background: #c5a66a;
      color: #171515;
    }

    .maison-bouton--contour {
      border-color: #d8be82;
      color: #f4eee0;
    }

    .maison-hero__image {
      margin-top: 2.5rem;
      display: flex;
      justify-content: center;
      overflow: hidden;
    }

    .maison-hero__image img {
      display: block;
      width: 100%;
      min-width: 900px;
      max-width: 1800px;
      height: auto;
    }

    .maison-etapes {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 2rem;
      max-width: 1100px;
      margin: 0 auto;
      padding: 4.5rem 1.5rem;
      text-align: center;
    }

    .maison-etapes article {
      padding: 1rem;
    }

    .maison-numero {
      color: #b89b5e;
      font-size: .9rem;
      letter-spacing: .2em;
    }

    .maison-etapes h2 {
      margin: .8rem 0;
      font-size: 1.5rem;
    }

    .maison-etapes article p {
      color: var(--couleur-texte-2);
      line-height: 1.7;
    }

    .maison-pro {
      padding: 4rem 1.5rem;
      background: #e9e2d4;
      color: #211f1c;
      text-align: center;
    }

    .maison-pro h2 {
      margin: 0 0 1rem;
      font-size: clamp(1.7rem, 3vw, 2.4rem);
    }

    .maison-pro > p:not(.maison-etiquette) {
      max-width: 650px;
      margin: 0 auto;
      line-height: 1.7;
      color: #5d574d;
    }

    .maison-etiquette--claire {
      color: #8c7040;
    }

    .maison-bouton--sombre {
      background: #211f1c;
      color: #f4eee0;
    }

    .maison-bouton--contour-sombre {
      border-color: #5d574d;
      color: #211f1c;
    }

    .maison-about {
      max-width: 850px;
      margin: 0 auto;
      padding: 4.5rem 1.5rem;
      text-align: center;
    }

    .maison-about h2 {
      margin: 0 0 1rem;
      font-size: clamp(1.6rem, 3vw, 2.2rem);
    }

    .maison-about > p:last-child {
      max-width: 700px;
      margin: 0 auto;
      color: var(--couleur-texte-2);
      line-height: 1.8;
    }

    @media (max-width: 760px) {
      .maison-hero {
        padding-top: 3rem;
      }

      .maison-etapes {
        grid-template-columns: 1fr;
        gap: 1rem;
        padding: 3rem 1.5rem;
      }

      .maison-hero__image img {
        min-width: 700px;
      }

      .maison-actions {
        flex-direction: column;
      }

      .maison-bouton {
        width: 100%;
        max-width: 320px;
      }
    }
  `],
})
export class HomePage {
  private readonly auth = inject(AuthService);
  protected readonly connected = this.auth.connected;
}
