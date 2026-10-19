import { Component, OnInit, ViewChild, inject, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SupabaseService } from '../../services/supabase.service';
import * as L from 'leaflet';

@Component({
  selector: 'app-trips',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatButtonModule,
    MatTooltipModule,
  ],
  templateUrl: './trips.component.html',
  styleUrl: './trips.component.scss',
})
export class TripsComponent implements OnInit, OnDestroy {
  private readonly supabaseService = inject(SupabaseService);

  displayedColumns: string[] = [
    'passengerName',
    'driverName',
    'fare',
    'distance_km',
    'message',
    'status',
    'created_at',
    'actions',
  ];
  dataSource = new MatTableDataSource<any>([]);

  selectedTripForMap: any = null;
  private routeMapInstance: L.Map | null = null;

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  async ngOnInit(): Promise<void> {
    await this.loadTrips();
  }

  ngOnDestroy(): void {
    if (this.routeMapInstance) {
      this.routeMapInstance.remove();
      this.routeMapInstance = null;
    }
  }

  async loadTrips(): Promise<void> {
    const trips = await this.supabaseService.getCompletedTrips();
    this.dataSource = new MatTableDataSource(trips);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  async openRouteMap(trip: any): Promise<void> {
    this.selectedTripForMap = trip;

    // Carga perezosa bajo demanda: sólo descarga los puntos de coordenadas al abrir el mapa
    if (!trip.route_points || trip.route_points.length === 0) {
      trip.route_points = await this.supabaseService.getTripRoutePoints(trip.id);
    }

    setTimeout(() => {
      this.renderRouteMap(trip);
    }, 150);
  }

  closeRouteMap(): void {
    if (this.routeMapInstance) {
      this.routeMapInstance.remove();
      this.routeMapInstance = null;
    }
    this.selectedTripForMap = null;
  }

  private renderRouteMap(trip: any): void {
    const mapElement = document.getElementById('routeMapModal');
    if (!mapElement) return;

    if (this.routeMapInstance) {
      this.routeMapInstance.remove();
      this.routeMapInstance = null;
    }

    const defaultCenter: L.LatLngExpression = [14.6349, -90.5069];
    this.routeMapInstance = L.map(mapElement).setView(defaultCenter, 14);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(this.routeMapInstance);

    const points: Array<[number, number]> = [];

    if (Array.isArray(trip.route_points) && trip.route_points.length > 0) {
      trip.route_points.forEach((pt: any) => {
        if (pt.lat != null && pt.lon != null) {
          points.push([pt.lat, pt.lon]);
        }
      });
    }

    const startIcon = L.divIcon({
      className: 'custom-route-marker start-marker',
      html: '<div style="background:#22c55e;width:14px;height:14px;border-radius:50%;border:2px solid #ffffff;box-shadow:0 0 10px #22c55e;"></div>',
      iconSize: [14, 14],
      iconAnchor: [7, 7],
    });

    const endIcon = L.divIcon({
      className: 'custom-route-marker end-marker',
      html: '<div style="background:#ef4444;width:14px;height:14px;border-radius:50%;border:2px solid #ffffff;box-shadow:0 0 10px #ef4444;"></div>',
      iconSize: [14, 14],
      iconAnchor: [7, 7],
    });

    const passengerIcon = L.divIcon({
      className: 'custom-route-marker passenger-marker',
      html: '<div style="background:#f9d423;width:16px;height:16px;border-radius:50%;border:2px solid #0f172a;box-shadow:0 0 12px #f9d423;"></div>',
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });

    if (points.length > 0) {
      const polyline = L.polyline(points, {
        color: '#f9d423',
        weight: 5,
        opacity: 0.9,
      }).addTo(this.routeMapInstance);

      // Marcador de inicio (TucTuc al aceptar)
      L.marker(points[0], { icon: startIcon })
        .addTo(this.routeMapInstance)
        .bindPopup('<b>Punto de inicio del TucTuc</b><br>Aceptación del viaje')
        .openPopup();

      // Marcador de fin (TucTuc al finalizar)
      if (points.length > 1) {
        L.marker(points[points.length - 1], { icon: endIcon })
          .addTo(this.routeMapInstance)
          .bindPopup(`<b>Fin del viaje</b><br>Cobro: ${trip.fare != null ? 'Q' + trip.fare.toFixed(2) : '-'}`);
      }

      // Marcador de pasajero si está disponible
      const safePassengerName = this.escapeHtml(trip.passengerName || 'Pasajero');
      if (trip.passenger_lat != null && trip.passenger_lon != null && trip.passenger_lat !== 0) {
        L.marker([trip.passenger_lat, trip.passenger_lon], { icon: passengerIcon })
          .addTo(this.routeMapInstance)
          .bindPopup(`<b>Punto de recogida:</b><br>${safePassengerName}`);
      }

      this.routeMapInstance.fitBounds(polyline.getBounds(), { padding: [40, 40], maxZoom: 16 });
    } else if (trip.passenger_lat != null && trip.passenger_lon != null && trip.passenger_lat !== 0) {
      const safePassengerName = this.escapeHtml(trip.passengerName || 'Pasajero');
      const pLatLng: [number, number] = [trip.passenger_lat, trip.passenger_lon];
      this.routeMapInstance.setView(pLatLng, 15);
      L.marker(pLatLng, { icon: passengerIcon })
        .addTo(this.routeMapInstance)
        .bindPopup(`<b>Ubicación del Pasajero:</b><br>${safePassengerName}`)
        .openPopup();
    }
  }

  private escapeHtml(raw: string): string {
    return String(raw)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  exportToCsv(): void {
    const data = this.dataSource.data;
    if (!data || data.length === 0) return;

    const headers = ['ID', 'Pasajero', 'Tipo Pasajero', 'Teléfono Pasajero', 'Conductor', 'Tarifa (Q)', 'Distancia (km)', 'Mensaje', 'Estado', 'Fecha'];
    const rows = data.map((t) => [
      `"${t.id || ''}"`,
      `"${(t.passengerName || '').replace(/"/g, '""')}"`,
      `"${t.isRegistered ? 'Registrado' : 'Sin cuenta (Invitado)'}"`,
      `"${(t.passengerPhone || '').replace(/"/g, '""')}"`,
      `"${(t.driverName || '').replace(/"/g, '""')}"`,
      `"${t.fare != null ? t.fare.toFixed(2) : ''}"`,
      `"${t.distance_km != null ? t.distance_km.toFixed(2) : ''}"`,
      `"${(t.message || '').replace(/"/g, '""')}"`,
      `"${t.status || ''}"`,
      `"${new Date(t.created_at).toLocaleString('es-GT')}"`,
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `historial_viajes_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
