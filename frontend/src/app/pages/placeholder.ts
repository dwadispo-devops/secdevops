import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-placeholder',
  template: `
    <section class="page">
      <h1>{{ titre }}</h1>
      <p>{{ texte }}</p>
    </section>
  `,
})
export class PlaceholderPage {
  private readonly route = inject(ActivatedRoute);
  readonly titre: string = this.route.snapshot.data['titre'];
  readonly texte: string = this.route.snapshot.data['texte'];
}
